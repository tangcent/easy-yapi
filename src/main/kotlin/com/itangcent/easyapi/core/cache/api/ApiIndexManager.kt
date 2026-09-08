package com.itangcent.easyapi.core.cache.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.itangcent.easyapi.core.dashboard.ApiScanner
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer
import com.itangcent.easyapi.core.export.recognizer.MetaAnnotationResolver
import com.itangcent.easyapi.core.internal.threading.IdeDispatchers
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.logging.IdeaLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Explains why a scan request was not executed. */
enum class ApiScanRejectionReason {
    NO_ACTIVE_SESSION,
    STALE_GENERATION,
    SESSION_STOPPED
}

/** Result of a controlled full or incremental scan request. */
sealed interface ApiScanResult {
    val generation: Long

    /** A scan completed and its result was accepted for the active generation. */
    data class Success(
        override val generation: Long,
        val endpointCount: Int
    ) : ApiScanResult

    /** A request was rejected before it could mutate the index. */
    data class Rejected(
        override val generation: Long,
        val reason: ApiScanRejectionReason
    ) : ApiScanResult

    /** A scan failed without replacing the last successful index snapshot. */
    data class Failed(
        override val generation: Long,
        val throwable: Throwable
    ) : ApiScanResult
}

internal interface ApiIndexScanExecutor {
    suspend fun scanAll(): List<ApiEndpoint>
    suspend fun scanClasses(classes: List<PsiClass>): List<ApiEndpoint>
}

private class ProjectApiIndexScanExecutor(project: Project) : ApiIndexScanExecutor {
    private val scanner = ApiScanner.getInstance(project)

    override suspend fun scanAll(): List<ApiEndpoint> = scanner.scanAll()

    override suspend fun scanClasses(classes: List<PsiClass>): List<ApiEndpoint> =
        scanner.scanClasses(classes).toList()
}

internal data class ApiIndexSessionSnapshot(
    val generation: Long,
    val continuous: Boolean,
    val initialScanPending: Boolean,
    val pendingFullRequests: Int,
    val pendingIncrementalRequests: Int
)

/**
 * Runs controller-owned API indexing sessions.
 *
 * Every continuous or one-shot session has an independent [SupervisorJob], a
 * generation token, and dedicated full and incremental request channels. A
 * stopped generation is never allowed to commit scan output to [ApiIndex].
 */
@Service(Service.Level.PROJECT)
class ApiIndexManager internal constructor(
    private val project: Project,
    private val apiIndex: ApiIndex,
    private val scanExecutor: ApiIndexScanExecutor,
    private val targetAnnotations: () -> Set<String>,
    private val dispatcher: CoroutineDispatcher,
    private val initialScanDelayMs: Long,
    private val minIncrementalScanIntervalMs: Long
) : Disposable, IdeaLog {

    constructor(project: Project) : this(
        project = project,
        apiIndex = ApiIndex.getInstance(project),
        scanExecutor = ProjectApiIndexScanExecutor(project),
        targetAnnotations = {
            CompositeApiClassRecognizer.getInstance(project).allTargetAnnotations.toSet()
        },
        dispatcher = IdeDispatchers.Background,
        initialScanDelayMs = DEFAULT_INITIAL_SCAN_DELAY_MS,
        minIncrementalScanIntervalMs = DEFAULT_INCREMENTAL_SCAN_INTERVAL_MS
    )

    private enum class SessionKind {
        CONTINUOUS,
        ONE_SHOT
    }

    private data class FullScanRequest(
        val source: String,
        val completion: CompletableDeferred<ApiScanResult>?
    )

    private data class IncrementalScanRequest(
        val source: String,
        val filePaths: List<String>,
        val completion: CompletableDeferred<ApiScanResult>?
    )

    private class ScanSession(
        val generation: Long,
        val kind: SessionKind,
        val job: Job,
        val scope: CoroutineScope,
        val fullChannel: Channel<FullScanRequest>,
        val incrementalChannel: Channel<IncrementalScanRequest>
    ) {
        val pendingFullRequests = AtomicInteger(0)
        val pendingIncrementalRequests = AtomicInteger(0)

        @Volatile
        var initialJob: Job? = null

        @Volatile
        var lastIncrementalScanTime: Long = 0L
    }

    private data class StopHandle(
        val generation: Long,
        val session: ScanSession?
    )

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LOG.warn("Uncaught coroutine exception in ApiIndexManager", throwable)
    }
    private val sessionLock = Any()
    private val generationCounter = AtomicLong(0L)

    @Volatile
    private var activeSession: ScanSession? = null

    /** Returns whether a continuous session is currently accepting work. */
    internal fun isStarted(): Boolean = activeSession?.let {
        it.kind == SessionKind.CONTINUOUS && it.job.isActive
    } == true

    /** Returns the current manager generation, including stopped generations. */
    fun currentGeneration(): Long = generationCounter.get()

    /**
     * Starts one continuous session if none is running.
     *
     * Repeated calls are idempotent and never schedule duplicate initial scans.
     */
    fun startContinuous(triggerInitialScan: Boolean = true): Long {
        val session = synchronized(sessionLock) {
            val current = activeSession
            if (current != null && current.kind == SessionKind.CONTINUOUS && current.job.isActive) {
                return current.generation
            }
            check(current == null || !current.job.isActive) {
                "Cannot start a continuous API scan session while one-shot work is active"
            }
            createSessionLocked(SessionKind.CONTINUOUS)
        }

        LOG.info(
            "API scan session started generation=${session.generation} " +
                "source=controller result=running"
        )
        if (triggerInitialScan) {
            scheduleInitialScan(session)
        }
        return session.generation
    }

    /** Compatibility alias for existing consumers. */
    fun start(triggerInitialScan: Boolean = true) {
        startContinuous(triggerInitialScan)
    }

    /**
     * Stops the active session immediately while retaining [ApiIndex].
     *
     * This method closes admission and cancels work without waiting for child
     * coroutines. Use [stopAndJoin] when completion must be observed.
     */
    fun stop() {
        stopContinuous()
    }

    /** Cancels and detaches the active session without waiting for scan code to return. */
    fun stopContinuous(): Long {
        val handle = detachActiveSession()
        LOG.info(
            "API scan session stopped generation=${handle.generation} " +
                "source=controller result=cancelled"
        )
        return handle.generation
    }

    /** Stops the active session and waits until all session children finish. */
    suspend fun stopAndJoin(): Long {
        val handle = detachActiveSession()
        handle.session?.job?.join()
        LOG.info(
            "API scan session stopped generation=${handle.generation} " +
                "source=controller result=cancelled"
        )
        return handle.generation
    }

    /**
     * Enqueues a full scan for the active continuous generation.
     *
     * The returned completion resolves after the scan commits, fails, or is
     * rejected. The manager never starts itself from this method.
     */
    fun requestFullScan(
        expectedGeneration: Long? = null,
        source: String = "compatibility"
    ): Deferred<ApiScanResult> {
        val completion = CompletableDeferred<ApiScanResult>()
        val session = synchronized(sessionLock) {
            val current = activeSession
            when {
                current == null || current.kind != SessionKind.CONTINUOUS || !current.job.isActive -> {
                    completion.complete(
                        ApiScanResult.Rejected(currentGeneration(), ApiScanRejectionReason.NO_ACTIVE_SESSION)
                    )
                    null
                }

                expectedGeneration != null && current.generation != expectedGeneration -> {
                    completion.complete(
                        ApiScanResult.Rejected(current.generation, ApiScanRejectionReason.STALE_GENERATION)
                    )
                    null
                }

                else -> current
            }
        }
        if (session != null) {
            enqueueFull(session, FullScanRequest(source, completion))
        }
        return completion
    }

    /** Compatibility request that never reads settings or starts a session. */
    fun requestScan() {
        val request = requestFullScan(source = "compatibility")
        if (request.isCompleted) {
            LOG.info(
                "API full scan request generation=${currentGeneration()} " +
                    "source=compatibility result=rejected"
            )
        }
    }

    /** Enqueues incremental work for the active continuous generation. */
    fun requestIncrementalScan(
        filePaths: List<String>,
        expectedGeneration: Long? = null,
        source: String = "compatibility"
    ): Deferred<ApiScanResult> {
        val completion = CompletableDeferred<ApiScanResult>()
        if (filePaths.isEmpty()) {
            completion.complete(ApiScanResult.Success(currentGeneration(), 0))
            return completion
        }

        val session = synchronized(sessionLock) {
            val current = activeSession
            when {
                current == null || current.kind != SessionKind.CONTINUOUS || !current.job.isActive -> {
                    completion.complete(
                        ApiScanResult.Rejected(currentGeneration(), ApiScanRejectionReason.NO_ACTIVE_SESSION)
                    )
                    null
                }

                expectedGeneration != null && current.generation != expectedGeneration -> {
                    completion.complete(
                        ApiScanResult.Rejected(current.generation, ApiScanRejectionReason.STALE_GENERATION)
                    )
                    null
                }

                else -> current
            }
        }
        if (session != null) {
            enqueueIncremental(
                session,
                IncrementalScanRequest(source, filePaths.distinct(), completion)
            )
        }
        return completion
    }

    /** Compatibility alias that only enqueues into an existing session. */
    suspend fun reIndex(filePaths: List<String>) {
        requestIncrementalScan(filePaths, source = "compatibility")
    }

    /**
     * Starts isolated one-shot work and returns its observable completion.
     *
     * The orchestration coroutine is independent from any continuous session;
     * the session itself still owns the scan worker and generation token.
     */
    fun runOneShotFullScanAsync(source: String = "manual-refresh"): Deferred<ApiScanResult> {
        val continuous = synchronized(sessionLock) {
            activeSession?.takeIf { it.kind == SessionKind.CONTINUOUS && it.job.isActive }
        }
        if (continuous != null) {
            return requestFullScan(continuous.generation, source)
        }

        val session = synchronized(sessionLock) {
            check(activeSession == null || activeSession?.job?.isActive == false) {
                "A one-shot API scan is already active"
            }
            createSessionLocked(SessionKind.ONE_SHOT)
        }
        LOG.info(
            "API one-shot scan started generation=${session.generation} " +
                "source=$source result=running"
        )

        val completion = CompletableDeferred<ApiScanResult>()
        enqueueFull(session, FullScanRequest(source, completion))
        val owner = SupervisorJob()
        val deferred = CoroutineScope(owner + dispatcher + exceptionHandler).async {
            try {
                completion.await()
            } finally {
                val handle = detachSession(session)
                handle.session?.job?.join()
                LOG.info(
                    "API one-shot scan stopped generation=${handle.generation} " +
                        "source=$source result=stopped"
                )
            }
        }
        deferred.invokeOnCompletion { owner.cancel() }
        return deferred
    }

    /**
     * Runs one full scan without creating continuous listeners.
     *
     * A failed scan leaves the retained cache untouched. If a continuous
     * session is already active, the request is routed through that session.
     */
    suspend fun runOneShotFullScan(source: String = "manual-refresh"): ApiScanResult =
        runOneShotFullScanAsync(source).await()

    internal fun sessionSnapshot(): ApiIndexSessionSnapshot? = activeSession?.let { session ->
        ApiIndexSessionSnapshot(
            generation = session.generation,
            continuous = session.kind == SessionKind.CONTINUOUS,
            initialScanPending = session.initialJob?.isActive == true,
            pendingFullRequests = session.pendingFullRequests.get(),
            pendingIncrementalRequests = session.pendingIncrementalRequests.get()
        )
    }

    private fun createSessionLocked(kind: SessionKind): ScanSession {
        val generation = generationCounter.incrementAndGet()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + dispatcher + exceptionHandler)
        val session = ScanSession(
            generation = generation,
            kind = kind,
            job = job,
            scope = scope,
            fullChannel = Channel(Channel.UNLIMITED),
            incrementalChannel = Channel(Channel.UNLIMITED)
        )
        activeSession = session
        scope.launch { processFullScans(session) }
        scope.launch { processIncrementalScans(session) }
        return session
    }

    private fun scheduleInitialScan(session: ScanSession) {
        synchronized(sessionLock) {
            if (!isCurrentLocked(session) || session.initialJob?.isActive == true) return
            session.initialJob = session.scope.launch {
                delay(initialScanDelayMs)
                enqueueFull(session, FullScanRequest("initial", null))
            }
        }
    }

    private fun enqueueFull(session: ScanSession, request: FullScanRequest) {
        if (!isCurrent(session)) {
            request.completion?.complete(
                ApiScanResult.Rejected(currentGeneration(), ApiScanRejectionReason.STALE_GENERATION)
            )
            return
        }
        session.pendingFullRequests.incrementAndGet()
        if (session.fullChannel.trySend(request).isFailure) {
            session.pendingFullRequests.decrementAndGet()
            request.completion?.complete(
                ApiScanResult.Rejected(currentGeneration(), ApiScanRejectionReason.SESSION_STOPPED)
            )
        }
    }

    private fun enqueueIncremental(session: ScanSession, request: IncrementalScanRequest) {
        if (!isCurrent(session)) {
            request.completion?.complete(
                ApiScanResult.Rejected(currentGeneration(), ApiScanRejectionReason.STALE_GENERATION)
            )
            return
        }
        session.pendingIncrementalRequests.incrementAndGet()
        if (session.incrementalChannel.trySend(request).isFailure) {
            session.pendingIncrementalRequests.decrementAndGet()
            request.completion?.complete(
                ApiScanResult.Rejected(currentGeneration(), ApiScanRejectionReason.SESSION_STOPPED)
            )
        }
    }

    private suspend fun processFullScans(session: ScanSession) {
        try {
            for (firstRequest in session.fullChannel) {
                val requests = mutableListOf(firstRequest)
                while (true) {
                    val additional = session.fullChannel.tryReceive().getOrNull() ?: break
                    requests += additional
                }
                try {
                    val source = requests.joinToString(",") { it.source }.take(160)
                    val result = executeFullScan(session, source)
                    requests.forEach { it.completion?.complete(result) }
                } catch (e: CancellationException) {
                    val rejected = ApiScanResult.Rejected(
                        currentGeneration(),
                        ApiScanRejectionReason.SESSION_STOPPED
                    )
                    requests.forEach { it.completion?.complete(rejected) }
                    throw e
                } finally {
                    session.pendingFullRequests.addAndGet(-requests.size)
                }
            }
        } finally {
            rejectQueuedFullRequests(session)
        }
    }

    private suspend fun processIncrementalScans(session: ScanSession) {
        try {
            for (firstRequest in session.incrementalChannel) {
                val requests = mutableListOf(firstRequest)
                while (true) {
                    val additional = session.incrementalChannel.tryReceive().getOrNull() ?: break
                    requests += additional
                }
                try {
                    throttleIncrementalSession(session)
                    val paths = requests.flatMap { it.filePaths }.distinct()
                    val source = requests.joinToString(",") { it.source }.take(160)
                    val result = executeIncrementalScan(session, paths, source)
                    requests.forEach { it.completion?.complete(result) }
                } catch (e: CancellationException) {
                    val rejected = ApiScanResult.Rejected(
                        currentGeneration(),
                        ApiScanRejectionReason.SESSION_STOPPED
                    )
                    requests.forEach { it.completion?.complete(rejected) }
                    throw e
                } finally {
                    session.pendingIncrementalRequests.addAndGet(-requests.size)
                }
            }
        } finally {
            rejectQueuedIncrementalRequests(session)
        }
    }

    private suspend fun executeFullScan(session: ScanSession, source: String): ApiScanResult {
        val startedAt = System.currentTimeMillis()
        return try {
            LOG.info(
                "API full scan generation=${session.generation} source=$source result=started"
            )
            val endpoints = scanExecutor.scanAll()
            // Split timings: scanAll() is the ApiScanner pass (already reported by
            // ApiScanMonitor), commit is the index swap + mutation publish. Without
            // this split a slow commit is indistinguishable from a slow scan.
            val scanMs = System.currentTimeMillis() - startedAt
            val mutation = synchronized(sessionLock) {
                if (!isCurrentLocked(session)) {
                    null
                } else {
                    apiIndex.replaceEndpointsSnapshot(endpoints)
                }
            }
            val commitMs = System.currentTimeMillis() - startedAt - scanMs
            if (mutation == null) {
                LOG.info(
                    "API full scan generation=${session.generation} source=$source " +
                        "result=stale elapsed=${System.currentTimeMillis() - startedAt}ms " +
                        "scan=${scanMs}ms endpoints=${endpoints.size}"
                )
                ApiScanResult.Rejected(currentGeneration(), ApiScanRejectionReason.STALE_GENERATION)
            } else {
                apiIndex.publishMutation(mutation, "API full scan committed")
                LOG.info(
                    "API full scan generation=${session.generation} source=$source " +
                        "result=success endpoints=${endpoints.size} " +
                        "elapsed=${System.currentTimeMillis() - startedAt}ms " +
                        "scan=${scanMs}ms commit=${commitMs}ms"
                )
                ApiScanResult.Success(session.generation, endpoints.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn(
                "API full scan generation=${session.generation} source=$source result=failed " +
                    "elapsed=${System.currentTimeMillis() - startedAt}ms",
                e
            )
            ApiScanResult.Failed(session.generation, e)
        }
    }

    private suspend fun executeIncrementalScan(
        session: ScanSession,
        filePaths: List<String>,
        source: String
    ): ApiScanResult {
        val startedAt = System.currentTimeMillis()
        return try {
            val changedClasses = findClassesFromFiles(filePaths)
            if (changedClasses.isEmpty()) {
                LOG.info(
                    "API incremental scan generation=${session.generation} source=$source " +
                        "result=no-api-classes"
                )
                return ApiScanResult.Success(session.generation, 0)
            }

            val changedClassNames = read {
                changedClasses.mapNotNull { it.qualifiedName }.toSet()
            }
            val scanStartedAt = System.currentTimeMillis()
            val endpoints = scanExecutor.scanClasses(changedClasses)
            val scanMs = System.currentTimeMillis() - scanStartedAt
            val classEndpoints = changedClassNames.associateWith { className ->
                endpoints.filter { it.className == className }
            }
            val mutation = synchronized(sessionLock) {
                if (!isCurrentLocked(session)) {
                    null
                } else {
                    apiIndex.updateEndpointsByClassesSnapshot(classEndpoints)
                }
            }
            val commitMs = System.currentTimeMillis() - scanStartedAt - scanMs
            if (mutation == null) {
                ApiScanResult.Rejected(currentGeneration(), ApiScanRejectionReason.STALE_GENERATION)
            } else {
                apiIndex.publishMutation(mutation, "API incremental scan committed")
                session.lastIncrementalScanTime = System.currentTimeMillis()
                LOG.info(
                    "API incremental scan generation=${session.generation} source=$source " +
                        "result=success endpoints=${endpoints.size} classes=${changedClasses.size} " +
                        "elapsed=${System.currentTimeMillis() - startedAt}ms " +
                        "scan=${scanMs}ms commit=${commitMs}ms"
                )
                ApiScanResult.Success(session.generation, endpoints.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn(
                "API incremental scan generation=${session.generation} source=$source " +
                    "result=failed elapsed=${System.currentTimeMillis() - startedAt}ms; " +
                    "scheduling full scan",
                e
            )
            enqueueFull(session, FullScanRequest("incremental-fallback", null))
            ApiScanResult.Failed(session.generation, e)
        }
    }

    private suspend fun throttleIncrementalSession(session: ScanSession) {
        val elapsed = System.currentTimeMillis() - session.lastIncrementalScanTime
        if (session.lastIncrementalScanTime > 0L && elapsed < minIncrementalScanIntervalMs) {
            delay(minIncrementalScanIntervalMs - elapsed)
        }
    }

    /** @requires Background context; PSI and VFS reads acquire ReadAction internally. */
    private suspend fun findClassesFromFiles(filePaths: List<String>): List<PsiClass> {
        val classes = mutableListOf<PsiClass>()
        filePaths.chunked(10).forEach { chunk ->
            val chunkClasses = read {
                val psiManager = PsiManager.getInstance(project)
                buildList {
                    chunk.forEach { filePath ->
                        try {
                            val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
                            if (virtualFile != null && virtualFile.exists()) {
                                val psiFile = psiManager.findFile(virtualFile)
                                addAll(
                                    psiFile?.children
                                        ?.filterIsInstance<PsiClass>()
                                        ?.filter(::isApiClassFast)
                                        .orEmpty()
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            LOG.warn("Failed to resolve changed API file path=$filePath", e)
                        }
                    }
                }
            }
            classes += chunkClasses
            yield()
        }
        return classes
    }

    private fun isApiClassFast(psiClass: PsiClass): Boolean {
        val annotationNames = psiClass.annotations.mapNotNull { it.qualifiedName }
        val targets = targetAnnotations()
        if (annotationNames.any { it in targets }) return true
        // Also match meta-annotated classes (e.g. a custom annotation that is
        // itself @RestController) so incremental scans align with the full scan
        // and don't fall back to a full re-scan for such classes.
        return MetaAnnotationResolver.hasMetaAnnotationSync(psiClass, targets)
    }

    private fun rejectQueuedFullRequests(session: ScanSession) {
        while (true) {
            val request = session.fullChannel.tryReceive().getOrNull() ?: break
            session.pendingFullRequests.decrementAndGet()
            request.completion?.complete(
                ApiScanResult.Rejected(currentGeneration(), ApiScanRejectionReason.SESSION_STOPPED)
            )
        }
    }

    private fun rejectQueuedIncrementalRequests(session: ScanSession) {
        while (true) {
            val request = session.incrementalChannel.tryReceive().getOrNull() ?: break
            session.pendingIncrementalRequests.decrementAndGet()
            request.completion?.complete(
                ApiScanResult.Rejected(currentGeneration(), ApiScanRejectionReason.SESSION_STOPPED)
            )
        }
    }

    private fun detachActiveSession(): StopHandle = synchronized(sessionLock) {
        detachSessionLocked(activeSession)
    }

    private fun detachSession(session: ScanSession): StopHandle = synchronized(sessionLock) {
        if (activeSession !== session) {
            StopHandle(currentGeneration(), null)
        } else {
            detachSessionLocked(session)
        }
    }

    private fun detachSessionLocked(session: ScanSession?): StopHandle {
        if (session == null) return StopHandle(currentGeneration(), null)
        activeSession = null
        val stoppedGeneration = generationCounter.incrementAndGet()
        session.initialJob?.cancel()
        session.fullChannel.close()
        session.incrementalChannel.close()
        session.scope.cancel()
        return StopHandle(stoppedGeneration, session)
    }

    private fun isCurrent(session: ScanSession): Boolean = synchronized(sessionLock) {
        isCurrentLocked(session)
    }

    private fun isCurrentLocked(session: ScanSession): Boolean =
        activeSession === session &&
            session.job.isActive &&
            generationCounter.get() == session.generation

    override fun dispose() {
        stop()
    }

    companion object {
        private const val DEFAULT_INITIAL_SCAN_DELAY_MS = 5_000L
        private const val DEFAULT_INCREMENTAL_SCAN_INTERVAL_MS = 10_000L

        fun getInstance(project: Project): ApiIndexManager = project.service()
    }
}
