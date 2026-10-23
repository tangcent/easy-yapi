package com.itangcent.easyapi.core.export

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.easyapi.core.internal.threading.readSync
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.waitUntil
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path

/**
 * Tests for [ExportTargetResolver]: the three target kinds, every [TargetFailure] type, the
 * all-or-nothing contract, and the files to sync before resolution ([ExportTargetResolver.targetFiles]).
 *
 * Classes are added inline with [loadFile]; the light fixture keeps them in an in-memory source
 * root, which is inside the project scope. File targets need real files under `project.basePath`
 * (the in-memory root is not on the local file system), so those tests write into [scratchDir]
 * and [tearDown] deletes it.
 */
class ExportTargetResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var scratchDir: File

    override fun setUp() {
        super.setUp()
        scratchDir = File(project.basePath!!, SCRATCH)
    }

    override fun tearDown() {
        try {
            if (::scratchDir.isInitialized && scratchDir.exists()) {
                scratchDir.deleteRecursively()
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(scratchDir)
            }
        } finally {
            super.tearDown()
        }
    }

    // ---- classes ----

    fun testClassResolvesByFullyQualifiedName() {
        addUserApi()

        val target = resolved(resolve(ExportTargets(classes = listOf(USER_API)))).single()

        assertEquals("kind", TargetKind.CLASS, target.kind)
        assertEquals("resolved", listOf(USER_API), target.resolved)
        assertEquals("element", USER_API, (target.elements.single() as PsiClass).qualifiedName)
    }

    fun testClassResolvesByUniqueSimpleName() {
        addUserApi()

        val target = resolved(resolve(ExportTargets(classes = listOf("UserApi")))).single()

        assertEquals("input is kept as passed", "UserApi", target.input)
        assertEquals("resolved to the FQN", listOf(USER_API), target.resolved)
    }

    fun testEntriesAreTrimmedAndDeduplicated() {
        addUserApi()

        val targets = resolved(resolve(ExportTargets(classes = listOf(" UserApi ", "UserApi"))))

        assertEquals("one target after trimming", 1, targets.size)
        assertEquals("trimmed input", "UserApi", targets.single().input)
    }

    /** `java.lang.String` lives in the mock JDK, a library: project scope must not see it. */
    fun testJdkClassIsNotAProjectClass() {
        val failure = singleFailure<TargetFailure.NotFound>(resolve(ExportTargets(classes = listOf("java.lang.String"))))

        assertEquals("kind", TargetKind.CLASS, failure.kind)
    }

    fun testDuplicateSimpleNameIsAmbiguousWithSortedCandidates() {
        loadFile("mcp/b/Dup.java", "package com.example.mcp.b; public class Dup {}")
        loadFile("mcp/a/Dup.java", "package com.example.mcp.a; public class Dup {}")
        awaitClasses("com.example.mcp.a.Dup", "com.example.mcp.b.Dup")

        val failure = singleFailure<TargetFailure.Ambiguous>(resolve(ExportTargets(classes = listOf("Dup"))))

        assertEquals("candidates", listOf("com.example.mcp.a.Dup", "com.example.mcp.b.Dup"), failure.candidates)
        assertEquals("total", 2, failure.total)
        assertTrue("hint: ${failure.hint}", failure.hint.contains("fully qualified name"))
    }

    fun testAmbiguityListsAtMostEightCandidates() {
        val fqns = (1..9).map { "com.example.mcp.m$it.Many" }
        (1..9).forEach { loadFile("mcp/m$it/Many.java", "package com.example.mcp.m$it; public class Many {}") }
        awaitClasses(*fqns.toTypedArray())

        val failure = singleFailure<TargetFailure.Ambiguous>(resolve(ExportTargets(classes = listOf("Many"))))

        assertEquals("total counts every match", 9, failure.total)
        assertEquals("the first eight, sorted", fqns.take(8), failure.candidates)
    }

    /**
     * One FQN declared in two files: the candidates are file paths, since only `files` can tell
     * them apart.
     */
    fun testSameQualifiedNameInTwoFilesListsTheFiles() {
        loadFile("mcp/twin1/Twin.java", "package com.example.mcp.twin; public class Twin {}")
        loadFile("mcp/twin2/Twin.java", "package com.example.mcp.twin; public class Twin {}")
        runBlocking {
            waitUntil {
                readSync {
                    JavaPsiFacade.getInstance(project)
                        .findClasses(TWIN, GlobalSearchScope.projectScope(project)).size
                } == 2
            }
        }

        val failure = singleFailure<TargetFailure.Ambiguous>(resolve(ExportTargets(classes = listOf(TWIN))))

        assertEquals("total", 2, failure.total)
        assertEquals("two distinct paths: ${failure.candidates}", 2, failure.candidates.toSet().size)
        assertTrue("paths: ${failure.candidates}", failure.candidates.all { it.endsWith("Twin.java") })
        assertTrue("hint: ${failure.hint}", failure.hint.contains("files"))
    }

    fun testBlankClassIsMalformed() {
        val failure = singleFailure<TargetFailure.Malformed>(resolve(ExportTargets(classes = listOf("   "))))

        assertEquals("kind", TargetKind.CLASS, failure.kind)
    }

    // ---- methods ----

    fun testMethodTargetCollectsEveryOverload() {
        addUserApi()

        val target = resolved(resolve(ExportTargets(methods = listOf("$USER_API#get")))).single()

        assertEquals("kind", TargetKind.METHOD, target.kind)
        assertEquals("both overloads", 2, target.elements.size)
        assertTrue("elements are methods", target.elements.all { it is PsiMethod })
        assertEquals(
            "signatures",
            setOf("$USER_API#get(Long)", "$USER_API#get(String)"),
            target.resolved.toSet()
        )
    }

    fun testMethodAcceptsASimpleClassName() {
        addUserApi()

        val target = resolved(resolve(ExportTargets(methods = listOf("UserApi#ping")))).single()

        assertEquals("signature", listOf("$USER_API#ping()"), target.resolved)
    }

    fun testInheritedMethodIsNotFoundOnTheSubclass() {
        loadFile(
            "mcp/web/BaseApi.java",
            """
            package com.example.mcp.web;

            public class BaseApi {

                public String ping() {
                    return null;
                }
            }
            """.trimIndent()
        )
        loadFile(
            "mcp/web/ChildApi.java",
            """
            package com.example.mcp.web;

            import com.example.mcp.web.BaseApi;

            public class ChildApi extends BaseApi {
            }
            """.trimIndent()
        )
        awaitClasses(BASE_API, CHILD_API)

        val failure = singleFailure<TargetFailure.NotFound>(resolve(ExportTargets(methods = listOf("$CHILD_API#ping"))))

        assertEquals("kind", TargetKind.METHOD, failure.kind)
        assertTrue("reason: ${failure.reason}", failure.reason.contains("no method 'ping' is declared in $CHILD_API"))
    }

    fun testMethodOfMissingClassIsNotFound() {
        val failure = singleFailure<TargetFailure.NotFound>(
            resolve(ExportTargets(methods = listOf("com.example.mcp.Missing#get")))
        )

        assertEquals("the failure names the whole entry", "com.example.mcp.Missing#get", failure.input)
        assertEquals("kind", TargetKind.METHOD, failure.kind)
    }

    /**
     * A signature as the result's `resolved` field shows it, passed back as a target: the reason must
     * point at the parameter list, not send the caller looking for an inherited method.
     */
    fun testMethodWithAParameterListIsMalformed() {
        addUserApi()

        val failure = singleFailure<TargetFailure.Malformed>(
            resolve(ExportTargets(methods = listOf("$USER_API#get(Long)")))
        )

        assertEquals("kind", TargetKind.METHOD, failure.kind)
        assertTrue("reason: ${failure.reason}", failure.reason.contains("without a parameter list"))
    }

    fun testMalformedMethodTargets() {
        val failed = failed(resolve(ExportTargets(methods = listOf("UserApi", "UserApi#", "#get", "A#b#c"))))

        assertEquals("every entry counted", 4, failed.targetCount)
        assertEquals("every entry failed", 4, failed.failures.size)
        assertTrue(
            "all malformed method targets: ${failed.failures}",
            failed.failures.all { it is TargetFailure.Malformed && it.kind == TargetKind.METHOD }
        )
    }

    // ---- files ----

    fun testFileTargetResolvesTheClassesItDeclares() {
        writeProjectFile(
            "web/UserFile.java",
            "package com.example.mcp.file; public class UserFile {} class UserFileHelper {}"
        )

        val target = resolved(resolve(ExportTargets(files = listOf("$SCRATCH/web/UserFile.java")))).single()

        assertEquals("kind", TargetKind.FILE, target.kind)
        // The classes, not the file: a scan then never re-reads the file, which may have changed by then.
        assertTrue("the elements are classes: ${target.elements}", target.elements.all { it is PsiClass })
        assertEquals(
            "the elements are every class the file declares",
            listOf("com.example.mcp.file.UserFile", "com.example.mcp.file.UserFileHelper"),
            target.elements.map { (it as PsiClass).qualifiedName }
        )
        assertEquals(
            "every class the file declares",
            listOf("com.example.mcp.file.UserFile", "com.example.mcp.file.UserFileHelper"),
            target.resolved
        )
    }

    fun testFileOutsideTheProjectIsMalformed() {
        val failure = singleFailure<TargetFailure.Malformed>(resolve(ExportTargets(files = listOf("../outside/X.java"))))

        assertEquals("kind", TargetKind.FILE, failure.kind)
    }

    fun testMissingFileIsNotFound() {
        val failure = singleFailure<TargetFailure.NotFound>(
            resolve(ExportTargets(files = listOf("$SCRATCH/Missing.java")))
        )

        assertEquals("kind", TargetKind.FILE, failure.kind)
    }

    fun testDirectoryIsMalformed() {
        writeProjectFile("pkg/Inside.java", "package com.example.mcp.pkg; public class Inside {}")

        val failure = singleFailure<TargetFailure.Malformed>(resolve(ExportTargets(files = listOf("$SCRATCH/pkg"))))

        assertTrue("reason: ${failure.reason}", failure.reason.contains("directory"))
    }

    fun testFileWithoutClassIsNoClass() {
        writeProjectFile("notes.txt", "not a source file")

        singleFailure<TargetFailure.NoClass>(resolve(ExportTargets(files = listOf("$SCRATCH/notes.txt"))))
    }

    // ---- all-or-nothing ----

    fun testOneFailureFailsTheWholeCallAndEveryFailureIsListed() {
        addUserApi()

        val failed = failed(
            resolve(ExportTargets(classes = listOf(USER_API, "NoSuchClass"), methods = listOf("UserApi")))
        )

        assertEquals("every target counted", 3, failed.targetCount)
        assertEquals("both bad targets, in input order", listOf("NoSuchClass", "UserApi"), failed.failures.map { it.input })
        assertEquals("their kinds", listOf(TargetKind.CLASS, TargetKind.METHOD), failed.failures.map { it.kind })
    }

    // ---- messages that hold once targets are synced before they resolve ----

    /** The sync finds a new class by its file; by name only once the IDE has indexed it. */
    fun testMissingClassSuggestsPassingItsFile() {
        val failure = singleFailure<TargetFailure.NotFound>(
            resolve(ExportTargets(classes = listOf("com.example.mcp.Fresh")))
        )

        assertTrue("reason: ${failure.reason}", failure.reason.contains("pass its file in `files`"))
    }

    /** After the sync a missing file is just missing: waiting does not help. */
    fun testMissingFileReasonOnlySaysItIsMissing() {
        val failure = singleFailure<TargetFailure.NotFound>(
            resolve(ExportTargets(files = listOf("$SCRATCH/Gone.java")))
        )

        assertEquals("reason", "no such file under the project root", failure.reason)
    }

    // ---- target files: what is synced before resolution ----

    /** A file target is its path under the project root, whether or not the file exists yet. */
    fun testTargetFilesResolvesFileTargetsAgainstTheProjectRoot() {
        writeProjectFile("web/Exists.java", "package com.example.mcp.file; public class Exists {}")

        val files = targetFiles(
            ExportTargets(
                files = listOf("$SCRATCH/web/Exists.java", " $SCRATCH/web/NotYet.java ", "$SCRATCH/web/Exists.java")
            )
        )

        val base = Path.of(project.basePath!!)
        assertEquals(
            "absolute paths, trimmed and de-duplicated",
            listOf("$SCRATCH/web/Exists.java", "$SCRATCH/web/NotYet.java").map { base.resolve(it).normalize() },
            files
        )
    }

    /**
     * Malformed entries are left to [ExportTargetResolver.resolve]. The fixture's classes live in an
     * in-memory source root, not on disk (its `temp://` file system still counts as a local one), so no
     * file is synced for them either; mapping a class or method target to its file on disk is covered
     * end to end.
     */
    fun testTargetFilesSkipsMalformedEntriesAndClassesOffTheLocalFileSystem() {
        addUserApi()

        val files = targetFiles(
            ExportTargets(
                classes = listOf(USER_API, "UserApi", " ", "NoSuchClass"),
                methods = listOf("$USER_API#get", "UserApi#ping", "UserApi", "#get", "A#b#c"),
                files = listOf("", "../outside/X.java", "bad\u0000path.java")
            )
        )

        assertEquals("nothing to sync", emptyList<Path>(), files)
    }

    // ---- Kotlin ----

    /** Resolution only: whether a Kotlin controller scans in the fixture is left to the end-to-end check. */
    fun testKotlinClassAndMethodResolve() {
        loadFile("api/yapi/KotlinController.kt")
        awaitClasses(KOTLIN_CTRL)

        val targets = resolved(
            resolve(ExportTargets(classes = listOf(KOTLIN_CTRL), methods = listOf("$KOTLIN_CTRL#newMethod")))
        )

        assertEquals("class", listOf(KOTLIN_CTRL), targets[0].resolved)
        assertEquals("method", listOf("$KOTLIN_CTRL#newMethod()"), targets[1].resolved)
    }

    // ---- helpers ----

    private fun resolve(targets: ExportTargets): TargetResolution =
        runBlocking { ExportTargetResolver(project).resolve(targets) }

    private fun targetFiles(targets: ExportTargets): List<Path> =
        runBlocking { ExportTargetResolver(project).targetFiles(targets) }

    private fun resolved(resolution: TargetResolution): List<ResolvedTarget> {
        assertTrue("expected Resolved, got $resolution", resolution is TargetResolution.Resolved)
        return (resolution as TargetResolution.Resolved).targets
    }

    private fun failed(resolution: TargetResolution): TargetResolution.Failed {
        assertTrue("expected Failed, got $resolution", resolution is TargetResolution.Failed)
        return resolution as TargetResolution.Failed
    }

    private inline fun <reified T : TargetFailure> singleFailure(resolution: TargetResolution): T {
        val failures = failed(resolution).failures
        assertEquals("exactly one failure: $failures", 1, failures.size)
        val failure = failures.single()
        assertTrue("expected ${T::class.simpleName}, got $failure", failure is T)
        return failure as T
    }

    private fun awaitClasses(vararg fqns: String) = runBlocking { fqns.forEach { waitForClass(it) } }

    private fun addUserApi() {
        loadFile(
            "mcp/web/UserApi.java",
            """
            package com.example.mcp.web;

            public class UserApi {

                public String get(Long id) {
                    return null;
                }

                public String get(String name) {
                    return null;
                }

                public void ping() {
                }
            }
            """.trimIndent()
        )
        awaitClasses(USER_API)
    }

    /** Writes a real file under [scratchDir] and registers it, with its directories, in the VFS. */
    private fun writeProjectFile(relativePath: String, text: String) {
        val ioFile = File(scratchDir, relativePath)
        ioFile.parentFile.mkdirs()
        ioFile.writeText(text)
        VfsUtil.findFileByIoFile(ioFile, true)
    }

    private companion object {
        const val SCRATCH = "mcp-target-resolver-test"
        const val USER_API = "com.example.mcp.web.UserApi"
        const val BASE_API = "com.example.mcp.web.BaseApi"
        const val CHILD_API = "com.example.mcp.web.ChildApi"
        const val TWIN = "com.example.mcp.twin.Twin"
        const val KOTLIN_CTRL = "com.itangcent.yapi.NewKotlinController"
    }
}
