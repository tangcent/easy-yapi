package com.itangcent.easyapi.core.export

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.replaceService
import com.intellij.util.ThrowableRunnable
import com.itangcent.easyapi.core.cache.api.ApiIndex
import com.itangcent.easyapi.core.dashboard.ApiScanner
import com.itangcent.easyapi.core.ide.support.SelectionScope
import com.itangcent.easyapi.core.util.json.GsonUtils
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.ResultLoader
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for [TargetedExportService]: a call without targets, the sync of files changed outside the IDE
 * that comes before resolution, the scan of each target kind, mixing classes with methods,
 * de-duplication, rejection, targets without endpoints (skipped, or the whole call failing when none
 * has one), and that a call never reads the project-wide index — not even when a target changes or
 * indexing starts while the call runs.
 *
 * Every test starts from a fresh, never-scanned [ApiIndex]; [tearDown] puts the original back so
 * the light project shared with later test classes keeps its own instance. Tests that replace the
 * scanner do the same through [withScanner]. The sync is a [FakeExternalChangeSync] ([useSync]): the
 * platform's needs the EDT, which this test holds while it waits for the call.
 */
class TargetedExportServiceTest : EasyApiLightCodeInsightFixtureTestCase() {

    private var originalIndex: ApiIndex? = null

    /** The sync the service calls; [setUp] installs one that reports success. */
    private lateinit var sync: FakeExternalChangeSync

    override fun createConfigReader() = TestConfigReader.empty(project)

    override fun setUp() {
        super.setUp()
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/ModelAttribute.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")
        loadFile("api/OrderCtrl.java", ORDER_CTRL_SOURCE)
        loadFile("api/PlainHelper.java", PLAIN_HELPER_SOURCE)
        runBlocking { listOf(USER_CTRL, ORDER_CTRL, PLAIN_HELPER).forEach { waitForClass(it) } }

        originalIndex = ApiIndex.getInstance(project)
        project.registerServiceInstance(ApiIndex::class.java, ApiIndex())
        useSync(FakeExternalChangeSync())
    }

    override fun tearDown() {
        try {
            originalIndex?.let { project.registerServiceInstance(ApiIndex::class.java, it) }
        } finally {
            super.tearDown()
        }
    }

    // ---- no target ----

    /** The index is ready and holds a sentinel on purpose: a call without targets must not read it. */
    fun testNoTargetIsTargetsRequired() {
        registerReadyIndex(sentinel())

        assertEquals("no list given", EndpointScanResult.TargetsRequired, scan(ExportTargets()))
        assertEquals(
            "three empty lists",
            EndpointScanResult.TargetsRequired,
            scan(ExportTargets(classes = emptyList(), methods = emptyList(), files = emptyList()))
        )
        assertTrue("rejected before the sync: ${sync.requests}", sync.requests.isEmpty())
    }

    // ---- files changed outside the IDE are synced before the targets resolve ----

    /** Unsaved editor changes stop the call before anything is scanned; the files come back as reported. */
    fun testUnsavedChangesInTheIdeStopTheCall() {
        val files = listOf("src/main/java/com/itangcent/api/UserCtrl.java", "C:/outside/Dto.java")
        useSync(FakeExternalChangeSync(SyncOutcome.UnsavedInIde(files)))
        var scans = 0

        val result = withScanner({ _, _ -> scans++; emptyList() }) { scan(ExportTargets(classes = listOf(USER_CTRL))) }

        assertEquals("the files, as the sync reported them", EndpointScanResult.UnsavedInIde(files), result)
        assertEquals("nothing scanned", 0, scans)
    }

    /**
     * A sync that timed out stops the call before the targets resolve: one target here does not
     * resolve, yet the call reports the timeout.
     */
    fun testSyncTimeoutStopsTheCallBeforeResolution() {
        useSync(FakeExternalChangeSync(SyncOutcome.TimedOut))
        var scans = 0

        val result = withScanner({ _, _ -> scans++; emptyList() }) {
            scan(ExportTargets(classes = listOf(USER_CTRL, "NoSuchCtrl")))
        }

        assertEquals("the timeout, not the unresolved target", EndpointScanResult.SyncTimeout, result)
        assertEquals("nothing scanned", 0, scans)
    }

    /**
     * One sync per call, with the file targets resolved against the project root. The fixture's classes
     * live in memory, so the class and method targets add no file; that mapping is covered end to end.
     */
    fun testSyncRunsOnceWithTheFileTargetPaths() {
        try {
            writeScratchFiles("FileCtrl.java" to FILE_CTRL_SOURCE)

            val listing = scanned(
                scan(
                    ExportTargets(
                        classes = listOf(USER_CTRL),
                        methods = listOf("$ORDER_CTRL#list"),
                        files = listOf("$FILE_SCRATCH/FileCtrl.java")
                    )
                )
            )

            assertEquals(
                "one sync, with the file target's absolute path",
                listOf(listOf(Path.of(project.basePath!!, FILE_SCRATCH, "FileCtrl.java").normalize())),
                sync.requests
            )
            assertEquals(
                "every target resolved and was scanned as usual",
                listOf(USER_CTRL, "$ORDER_CTRL#list", "$FILE_SCRATCH/FileCtrl.java"),
                listing.targets.map { it.input }
            )
            assertTrue("nothing skipped: ${listing.skipped}", listing.skipped.isEmpty())
        } finally {
            deleteScratchFiles()
        }
    }

    /**
     * The sync comes before resolution: a file that only the sync brings in (here the fake writes it)
     * resolves and is scanned in the same call. Resolved before the sync, it would not be found.
     */
    fun testFileTheSyncBringsInIsResolvedInTheSameCall() {
        val target = "$FILE_SCRATCH/FileCtrl.java"
        useSync(FakeExternalChangeSync(onSync = { writeScratchFiles("FileCtrl.java" to FILE_CTRL_SOURCE) }))
        try {
            assertNull(
                "precondition: the IDE does not know the file before the call",
                LocalFileSystem.getInstance().findFileByIoFile(File(project.basePath!!, target))
            )

            val listing = scanned(scan(ExportTargets(files = listOf(target))))

            assertEquals("resolved", listOf("com.itangcent.api.file.FileCtrl"), listing.targets.single().resolved)
            assertEquals("paths", listOf("/file/ping"), listing.endpoints.map { it.path })
        } finally {
            deleteScratchFiles()
        }
    }

    // ---- targets: scan now ----

    fun testClassTargetListsOnlyThatClass() {
        val listing = scanned(scan(ExportTargets(classes = listOf(USER_CTRL))))

        assertEquals("targets", listOf(TargetSummary("class", USER_CTRL, listOf(USER_CTRL))), listing.targets)
        assertTrue("nothing skipped: ${listing.skipped}", listing.skipped.isEmpty())
        assertTrue("UserCtrl declares endpoints", listing.endpoints.isNotEmpty())
        assertEquals("count", listing.endpoints.size, listing.count)
        assertTrue(
            "only UserCtrl endpoints: ${listing.endpoints.map { it.className }}",
            listing.endpoints.all { it.className?.startsWith(USER_CTRL) == true }
        )

        // Re-parsed and pretty-printed so the snapshot is reviewable; keys and values match the wire text.
        val actual = GsonUtils.prettyJson(JsonParser.parseString(listing.toJson()))
        assertEquals(
            "wire format",
            ResultLoader.load(TargetedExportServiceTest::class.java, "classTarget"),
            actual
        )
    }

    fun testSimpleClassNameListsTheSameEndpoints() {
        val byFqn = scanned(scan(ExportTargets(classes = listOf(USER_CTRL))))
        val bySimpleName = scanned(scan(ExportTargets(classes = listOf("UserCtrl"))))

        assertEquals("same endpoints", byFqn.endpoints, bySimpleName.endpoints)
        assertEquals("resolved to the FQN", listOf(USER_CTRL), bySimpleName.targets.single().resolved)
    }

    fun testMethodTargetListsOnlyThatMethod() {
        val listing = scanned(scan(ExportTargets(methods = listOf("$USER_CTRL#greeting"))))

        assertEquals("one endpoint", 1, listing.count)
        assertEquals("from greeting", "greeting", listing.endpoints.single().methodName)
        assertEquals("signature", listOf("$USER_CTRL#greeting()"), listing.targets.single().resolved)
    }

    /** Regression guard: scanSelection drops the classes of a selection that also holds methods. */
    fun testClassAndMethodTargetsAreBothHonoured() {
        val listing = scanned(scan(ExportTargets(classes = listOf(ORDER_CTRL), methods = listOf("UserCtrl#greeting"))))

        val byClass = listing.endpoints.groupBy { it.className }
        assertEquals("only the two named classes", setOf(ORDER_CTRL, USER_CTRL), byClass.keys)
        assertEquals(
            "every OrderCtrl endpoint",
            setOf("list", "create"),
            byClass.getValue(ORDER_CTRL).map { it.methodName }.toSet()
        )
        assertEquals(
            "only greeting from UserCtrl",
            listOf("greeting"),
            byClass.getValue(USER_CTRL).map { it.methodName }
        )
    }

    fun testMethodInsideATargetedClassIsListedOnce() {
        val classOnly = scanned(scan(ExportTargets(classes = listOf(USER_CTRL))))
        val overlapping = scanned(scan(ExportTargets(classes = listOf(USER_CTRL), methods = listOf("$USER_CTRL#greeting"))))

        assertEquals("greeting once", 1, overlapping.endpoints.count { it.methodName == "greeting" })
        assertEquals("same endpoints as the class alone", classOnly.endpoints, overlapping.endpoints)
    }

    fun testUnresolvedTargetRejectsTheWholeCall() {
        val result = scan(ExportTargets(classes = listOf(USER_CTRL, "NoSuchCtrl")))

        assertTrue("expected TargetsUnresolved, got $result", result is EndpointScanResult.TargetsUnresolved)
        val message = (result as EndpointScanResult.TargetsUnresolved).message
        assertTrue("counts the failure: $message", message.startsWith("1 of 2 target(s)"))
        assertTrue("names the bad target: $message", message.contains("- class 'NoSuchCtrl'"))
    }

    /**
     * A target without endpoints is reported as such, never answered from the project-wide index. The
     * index is ready on purpose: falling back to a never-scanned index would hang instead of failing.
     */
    fun testOnlyTargetsWithoutEndpointsAreNoEndpointsNeverTheIndex() {
        registerReadyIndex(sentinel())

        assertEquals(
            "a class without endpoints",
            EndpointScanResult.NoEndpoints(listOf(SkippedTarget.of(TargetKind.CLASS, PLAIN_HELPER))),
            scan(ExportTargets(classes = listOf(PLAIN_HELPER)))
        )
        assertEquals(
            "a method without endpoints",
            EndpointScanResult.NoEndpoints(listOf(SkippedTarget.of(TargetKind.METHOD, "$PLAIN_HELPER#help"))),
            scan(ExportTargets(methods = listOf("$PLAIN_HELPER#help")))
        )
    }

    // ---- targets without endpoints next to targets with endpoints ----

    /** A model or service passed along with a controller: the controller is listed, the other skipped. */
    fun testClassTargetWithoutEndpointsIsSkippedAndTheRestListed() {
        val listing = scanned(scan(ExportTargets(classes = listOf(PLAIN_HELPER, USER_CTRL))))

        assertEquals("skipped", listOf(SkippedTarget.of(TargetKind.CLASS, PLAIN_HELPER)), listing.skipped)
        assertEquals(
            "UserCtrl is listed as on its own",
            scanned(scan(ExportTargets(classes = listOf(USER_CTRL)))).endpoints,
            listing.endpoints
        )
        assertEquals(
            "every target still shows how it resolved",
            listOf(PLAIN_HELPER, USER_CTRL),
            listing.targets.map { it.input }
        )
    }

    /**
     * Method targets of one class share a scan, so each endpoint has to be traced back to the method
     * target it came from: here only the unmapped method is skipped.
     */
    fun testMethodTargetWithoutAnEndpointIsSkippedNextToOneOfItsClass() {
        loadFile("api/mcp/MixedCtrl.java", MIXED_CTRL_SOURCE)
        runBlocking { waitForClass(MIXED_CTRL) }

        val listing = scanned(scan(ExportTargets(methods = listOf("$MIXED_CTRL#mapped", "$MIXED_CTRL#unmapped"))))

        assertEquals("paths", listOf("/mixed/mapped"), listing.endpoints.map { it.path })
        assertEquals("skipped", listOf(SkippedTarget.of(TargetKind.METHOD, "$MIXED_CTRL#unmapped")), listing.skipped)
    }

    /**
     * The endpoints of contract methods come from the implementation, so the trace back to a method
     * target has to follow the override: the mapped contract method is listed, the unmapped one skipped.
     */
    fun testContractMethodTargetsAreTracedThroughTheImplementation() {
        loadFile("api/mcp/TraceContract.java", TRACE_CONTRACT_SOURCE)
        loadFile("api/mcp/TraceContractImpl.java", TRACE_CONTRACT_IMPL_SOURCE)
        runBlocking { listOf(TRACE_CONTRACT, TRACE_CONTRACT_IMPL).forEach { waitForClass(it) } }

        val listing = scanned(
            scan(ExportTargets(methods = listOf("$TRACE_CONTRACT#mapped", "$TRACE_CONTRACT#unmapped")))
        )

        assertEquals("paths", listOf("/trace/mapped"), listing.endpoints.map { it.path })
        assertEquals(
            "skipped",
            listOf(SkippedTarget.of(TargetKind.METHOD, "$TRACE_CONTRACT#unmapped")),
            listing.skipped
        )
    }

    // ---- one scan per target ----

    /**
     * A contract (mapping annotations on an interface, `@RestController` on its implementation) next
     * to an ordinary controller keeps its endpoints, which count for the contract target although they
     * come from its implementation.
     */
    fun testContractClassNextToAControllerKeepsItsEndpoints() {
        addContract()

        val listing = scanned(scan(ExportTargets(classes = listOf(CONTRACT, ORDER_CTRL))))

        assertEquals(
            "endpoints of both targets",
            setOf("/hello", "/save", "/order/list", "/order/create"),
            listing.endpoints.map { it.path }.toSet()
        )
    }

    /** The same for method targets, which are scanned per declaring class. */
    fun testContractMethodNextToAControllerMethodKeepsItsEndpoint() {
        addContract()

        val listing = scanned(scan(ExportTargets(methods = listOf("$CONTRACT#save", "$ORDER_CTRL#list"))))

        assertEquals(
            "one endpoint per method target",
            setOf("/save", "/order/list"),
            listing.endpoints.map { it.path }.toSet()
        )
    }

    // ---- the call is not trusted when something changed under it ----

    /**
     * A method deleted after it resolved — here while the other target is scanned — must not reach
     * the scanner: a method without its class makes `scanSelection` fall back to the whole-project
     * index, which is ready here on purpose.
     */
    fun testTargetDeletedBeforeItsScanIsRejected() {
        registerReadyIndex(sentinel())
        loadFile("api/mcp/ShiftingCtrl.java", SHIFTING_CTRL_SOURCE)
        runBlocking { waitForClass(SHIFTING_CTRL) }
        val realScanner = ApiScanner.getInstance(project)
        var scans = 0

        val result = withScanner({ selection, indicator ->
            if (scans++ == 0) {
                val gone = findMethod(findClass(SHIFTING_CTRL)!!, "gone")!!
                WriteCommandAction.runWriteCommandAction(project) { gone.delete() }
            }
            realScanner.scanSelection(selection, indicator)
        }) {
            scan(ExportTargets(classes = listOf(ORDER_CTRL), methods = listOf("$SHIFTING_CTRL#gone")))
        }

        assertEquals("rejected, not answered from the index", EndpointScanResult.TargetsChanged, result)
    }

    /**
     * A method deleted while its own scan runs gets past the check before the scan, and `scanSelection`,
     * finding no class for it, answers from the whole-project index, which is ready here on purpose. The
     * check after the scan must reject that answer.
     */
    fun testTargetDeletedDuringItsScanIsRejected() {
        registerReadyIndex(sentinel())
        loadFile("api/mcp/ShiftingCtrl.java", SHIFTING_CTRL_SOURCE)
        runBlocking { waitForClass(SHIFTING_CTRL) }
        val realScanner = ApiScanner.getInstance(project)
        var scans = 0
        var answeredFromTheIndex = false

        val result = withScanner({ selection, indicator ->
            if (scans++ == 1) {
                val gone = findMethod(findClass(SHIFTING_CTRL)!!, "gone")!!
                WriteCommandAction.runWriteCommandAction(project) { gone.delete() }
            }
            realScanner.scanSelection(selection, indicator).also { found ->
                answeredFromTheIndex = answeredFromTheIndex || found.any { it.name == SENTINEL }
            }
        }) {
            scan(ExportTargets(classes = listOf(ORDER_CTRL), methods = listOf("$SHIFTING_CTRL#gone")))
        }

        assertTrue("precondition: the scan of the method answered from the index", answeredFromTheIndex)
        assertEquals("rejected, not answered from the index", EndpointScanResult.TargetsChanged, result)
    }

    /**
     * Indexing that starts while the targets are scanned can make an exporter fail quietly (the
     * scanner logs the failure and goes on), so such a scan is reported as INDEX_NOT_READY rather
     * than as a success that may be missing endpoints.
     */
    fun testIndexingDuringTheScanIsIndexNotReady() {
        val result = withScanner({ _, _ ->
            DumbModeTestUtils.runInDumbModeSynchronously(project, ThrowableRunnable<Throwable> {})
            emptyList()
        }) {
            scan(ExportTargets(classes = listOf(USER_CTRL)))
        }

        assertEquals("a scan that overlapped indexing", EndpointScanResult.IndexNotReady, result)
    }

    /** A wait for smart mode that runs out, here inside the scanner, is INDEX_NOT_READY. */
    fun testWaitForSmartModeThatRunsOutIsIndexNotReady() {
        val result = withScanner({ _, _ ->
            withTimeout(1.milliseconds) { delay(1_000) }
            emptyList()
        }) {
            scan(ExportTargets(classes = listOf(USER_CTRL)))
        }

        assertEquals("a wait that ran out", EndpointScanResult.IndexNotReady, result)
    }

    /** The same timeout in a call cancelled meanwhile: the call throws its cancellation instead. */
    fun testCancelledCallIsNotTakenForAWaitThatRanOut() {
        var outcome: Any? = null

        withScanner({ _, _ ->
            val ranOut = try {
                withTimeout(1.milliseconds) { delay(1_000) }
                null
            } catch (e: TimeoutCancellationException) {
                e
            }
            currentCoroutineContext().cancel()
            throw ranOut!!
        }) {
            runBlocking {
                // A job of its own, so that cancelling it leaves runBlocking alone.
                launch(Job()) {
                    outcome = try {
                        TargetedExportService.getInstance(project).scanEndpoints(ExportTargets(classes = listOf(USER_CTRL)))
                    } catch (e: CancellationException) {
                        e
                    }
                }.join()
            }
        }

        assertTrue("the cancellation, not INDEX_NOT_READY: $outcome", outcome is CancellationException)
    }

    /** Dumb mode that comes back between a wait and an index lookup: the lookup's exception is INDEX_NOT_READY. */
    fun testIndexLookupDuringIndexingIsIndexNotReady() {
        val result = withScanner({ _, _ -> throw IndexNotReadyException.create() }) {
            scan(ExportTargets(classes = listOf(USER_CTRL)))
        }

        assertEquals("an index lookup in dumb mode", EndpointScanResult.IndexNotReady, result)
    }

    // ---- wire format of what a scan returns ----

    /** gRPC endpoints are listed too; they have no HTTP method, so the field is left out of the JSON. */
    fun testGrpcEndpointIsListedWithoutAnHttpMethod() {
        val http = ApiEndpoint(
            name = "getUser",
            className = "com.example.UserController",
            metadata = HttpMetadata(path = "/users/{id}", method = HttpMethod.GET)
        )
        val grpc = ApiEndpoint(
            name = "SayHello",
            className = "com.example.GreeterService",
            metadata = GrpcMetadata(
                path = "/com.example.Greeter/SayHello",
                serviceName = "Greeter",
                methodName = "SayHello",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY
            )
        )

        val listing = scanned(withScanner({ _, _ -> listOf(http, grpc) }) { scan(ExportTargets(classes = listOf(USER_CTRL))) })

        assertEquals("count", 2, listing.count)
        val httpSummary = listing.endpoints.single { it.protocol == "HTTP" }
        assertEquals("http method", "GET", httpSummary.httpMethod)
        assertEquals("http path", "/users/{id}", httpSummary.path)
        val grpcSummary = listing.endpoints.single { it.protocol == "gRPC" }
        assertNull("gRPC has no HTTP method", grpcSummary.httpMethod)
        assertEquals("gRPC path", "/com.example.Greeter/SayHello", grpcSummary.path)

        val grpcJson = JsonParser.parseString(listing.toJson()).asJsonObject
            .getAsJsonArray("endpoints")
            .map { it.asJsonObject }
            .single { it.get("protocol").asString == "gRPC" }
        assertFalse("null fields are omitted on the wire: $grpcJson", grpcJson.has("httpMethod"))
    }

    /**
     * The whole chain for a file target. The file lives under `project.basePath`, outside the
     * fixture's source root.
     */
    fun testFileTargetListsTheClassesItDeclares() {
        try {
            writeScratchFiles("FileCtrl.java" to FILE_CTRL_SOURCE)

            val listing = scanned(scan(ExportTargets(files = listOf("$FILE_SCRATCH/FileCtrl.java"))))

            assertEquals("resolved", listOf("com.itangcent.api.file.FileCtrl"), listing.targets.single().resolved)
            assertEquals("paths", listOf("/file/ping"), listing.endpoints.map { it.path })
        } finally {
            deleteScratchFiles()
        }
    }

    /**
     * A file target counts as a whole: a file declaring a controller and a helper is not skipped, a
     * file declaring only plain classes is.
     */
    fun testFileTargetIsSkippedOnlyWhenNoClassInItHasAnEndpoint() {
        try {
            writeScratchFiles("FileCtrl.java" to FILE_CTRL_WITH_HELPER_SOURCE, "FilePlain.java" to FILE_PLAIN_SOURCE)

            val listing = scanned(
                scan(ExportTargets(files = listOf("$FILE_SCRATCH/FileCtrl.java", "$FILE_SCRATCH/FilePlain.java")))
            )

            assertEquals(
                "the controller file resolved to both of its classes",
                listOf("com.itangcent.api.file.FileCtrl", "com.itangcent.api.file.FileCtrlHelper"),
                listing.targets.first().resolved
            )
            assertEquals("paths", listOf("/file/ping"), listing.endpoints.map { it.path })
            assertEquals(
                "only the file without an API class is skipped",
                listOf(SkippedTarget.of(TargetKind.FILE, "$FILE_SCRATCH/FilePlain.java")),
                listing.skipped
            )
        } finally {
            deleteScratchFiles()
        }
    }

    // ---- helpers ----

    /**
     * Writes real files into `project.basePath/[FILE_SCRATCH]`, outside the fixture's source root, and
     * waits until the IDE is done indexing. Re-creating a directory the VFS already knows (an earlier
     * test deleted it) queues a short dumb-mode pass; a call overlapping it is rightly answered with
     * INDEX_NOT_READY, so the test waits it out first.
     */
    private fun writeScratchFiles(vararg files: Pair<String, String>) {
        val dir = File(project.basePath!!, FILE_SCRATCH)
        dir.mkdirs()
        for ((name, source) in files) {
            val ioFile = File(dir, name)
            ioFile.writeText(source)
            VfsUtil.findFileByIoFile(ioFile, true)
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    private fun deleteScratchFiles() {
        val dir = File(project.basePath!!, FILE_SCRATCH)
        dir.deleteRecursively()
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir)
    }

    private fun scan(targets: ExportTargets): EndpointScanResult =
        runBlocking { TargetedExportService.getInstance(project).scanEndpoints(targets) }

    /** Makes [fake] the project's sync until the test ends. */
    private fun useSync(fake: FakeExternalChangeSync) {
        sync = fake
        project.replaceService(ExternalChangeSync::class.java, fake, testRootDisposable)
    }

    private fun scanned(result: EndpointScanResult): ScanReport {
        assertTrue("expected Scanned, got $result", result is EndpointScanResult.Scanned)
        return (result as EndpointScanResult.Scanned).report
    }

    /** Replaces the index with one whose first update has completed, so `isReady()` is true. */
    private fun registerReadyIndex(vararg endpoints: ApiEndpoint) {
        val index = ApiIndex()
        runBlocking { index.updateEndpoints(endpoints.toList()) }
        project.registerServiceInstance(ApiIndex::class.java, index)
    }

    /** An endpoint no fixture class declares: seeing it in a result means the call read the index. */
    private fun sentinel() = ApiEndpoint(
        name = SENTINEL,
        className = "com.example.Sentinel",
        metadata = HttpMetadata(path = "/sentinel", method = HttpMethod.GET)
    )

    /**
     * Runs [block] with every `ApiScanner.scanSelection` call answered by [answer], then puts the
     * real scanner back (the light project is shared with later test classes).
     */
    private fun <T> withScanner(
        answer: suspend (SelectionScope, ProgressIndicator?) -> List<ApiEndpoint>,
        block: () -> T
    ): T {
        val original = ApiScanner.getInstance(project)
        val scanner = mock<ApiScanner> {
            onBlocking { scanSelection(anyOrNull(), anyOrNull()) } doSuspendableAnswer { invocation ->
                answer(invocation.getArgument(0), invocation.getArgument(1))
            }
        }
        project.registerServiceInstance(ApiScanner::class.java, scanner)
        try {
            return block()
        } finally {
            project.registerServiceInstance(ApiScanner::class.java, original)
        }
    }

    /** A contract interface and its `@RestController` implementation, like `ContractSelectionExportTest`. */
    private fun addContract() {
        loadFile("api/mcp/GreetContract.java", CONTRACT_SOURCE)
        loadFile("api/mcp/GreetContractImpl.java", CONTRACT_IMPL_SOURCE)
        runBlocking { listOf(CONTRACT, CONTRACT_IMPL).forEach { waitForClass(it) } }
    }

    private companion object {
        const val USER_CTRL = "com.itangcent.api.UserCtrl"
        const val ORDER_CTRL = "com.itangcent.api.OrderCtrl"
        const val PLAIN_HELPER = "com.itangcent.api.PlainHelper"
        const val CONTRACT = "com.itangcent.api.mcp.GreetContract"
        const val CONTRACT_IMPL = "com.itangcent.api.mcp.GreetContractImpl"
        const val SHIFTING_CTRL = "com.itangcent.api.mcp.ShiftingCtrl"
        const val MIXED_CTRL = "com.itangcent.api.mcp.MixedCtrl"
        const val TRACE_CONTRACT = "com.itangcent.api.mcp.TraceContract"
        const val TRACE_CONTRACT_IMPL = "com.itangcent.api.mcp.TraceContractImpl"
        const val SENTINEL = "SENTINEL"
        const val FILE_SCRATCH = "mcp-export-service-test"

        val MIXED_CTRL_SOURCE = """
            package com.itangcent.api.mcp;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping("/mixed")
            public class MixedCtrl {

                @GetMapping("/mapped")
                public String mapped() {
                    return null;
                }

                public String unmapped() {
                    return null;
                }
            }
        """.trimIndent()

        val TRACE_CONTRACT_SOURCE = """
            package com.itangcent.api.mcp;

            import com.itangcent.model.Result;
            import org.springframework.web.bind.annotation.GetMapping;

            public interface TraceContract {

                @GetMapping("/trace/mapped")
                Result mapped();

                Result unmapped();
            }
        """.trimIndent()

        val TRACE_CONTRACT_IMPL_SOURCE = """
            package com.itangcent.api.mcp;

            import com.itangcent.api.mcp.TraceContract;
            import com.itangcent.model.Result;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class TraceContractImpl implements TraceContract {

                @Override
                public Result mapped() {
                    return null;
                }

                @Override
                public Result unmapped() {
                    return null;
                }
            }
        """.trimIndent()

        val CONTRACT_SOURCE = """
            package com.itangcent.api.mcp;

            import com.itangcent.model.Result;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PostMapping;

            public interface GreetContract {

                @GetMapping("/hello")
                Result hello();

                @PostMapping("/save")
                Result save(String name);
            }
        """.trimIndent()

        val CONTRACT_IMPL_SOURCE = """
            package com.itangcent.api.mcp;

            import com.itangcent.api.mcp.GreetContract;
            import com.itangcent.model.Result;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class GreetContractImpl implements GreetContract {

                @Override
                public Result hello() {
                    return null;
                }

                @Override
                public Result save(String name) {
                    return null;
                }
            }
        """.trimIndent()

        val SHIFTING_CTRL_SOURCE = """
            package com.itangcent.api.mcp;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping("/shifting")
            public class ShiftingCtrl {

                @GetMapping("/stay")
                public String stay() {
                    return null;
                }

                @GetMapping("/gone")
                public String gone() {
                    return null;
                }
            }
        """.trimIndent()

        val ORDER_CTRL_SOURCE = """
            package com.itangcent.api;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            /**
             * apis about order
             */
            @RestController
            @RequestMapping("/order")
            public class OrderCtrl {

                /**
                 * list orders
                 */
                @GetMapping("/list")
                public String list() {
                    return null;
                }

                /**
                 * create an order
                 */
                @PostMapping("/create")
                public String create(String name) {
                    return null;
                }
            }
        """.trimIndent()

        val PLAIN_HELPER_SOURCE = """
            package com.itangcent.api;

            public class PlainHelper {

                public String help() {
                    return null;
                }
            }
        """.trimIndent()

        val FILE_CTRL_SOURCE = """
            package com.itangcent.api.file;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping("/file")
            public class FileCtrl {

                @GetMapping("/ping")
                public String ping() {
                    return null;
                }
            }
        """.trimIndent()

        val FILE_CTRL_WITH_HELPER_SOURCE = """
            package com.itangcent.api.file;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping("/file")
            public class FileCtrl {

                @GetMapping("/ping")
                public String ping() {
                    return null;
                }
            }

            class FileCtrlHelper {

                String help() {
                    return null;
                }
            }
        """.trimIndent()

        val FILE_PLAIN_SOURCE = """
            package com.itangcent.api.file;

            public class FilePlain {

                public String plain() {
                    return null;
                }
            }
        """.trimIndent()
    }
}
