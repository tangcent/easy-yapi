package com.itangcent.easyapi.cache

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.dashboard.ApiScanner
import com.itangcent.easyapi.exporter.core.CompositeApiClassRecognizer
import com.itangcent.easyapi.ide.DumbModeHelper
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED

/**
 * Manages the API endpoint index with support for full and incremental scans.
 *
 * Coordinates between file change events and the API index cache:
 * - Full scans: Triggered on startup or manual refresh
 * - Incremental scans: Triggered by file changes
 *
 * ## Architecture
 * Uses channels for work coordination:
 * - `fullScanChannel`: Conflated channel for full scan requests
 * - `incrementalScanChannel`: Unlimited channel for file change batches
 *
 * ## Thread Safety
 * Uses SupervisorJob and exception handler to prevent cascade failures.
 *
 * @see ApiIndex for the cached endpoint storage
 * @see ApiFileChangeListener for file change detection
 */
@Service(Service.Level.PROJECT)
class ApiIndexManager(private val project: Project) : Disposable, IdeaLog {

    private val apiScanner: ApiScanner = ApiScanner.getInstance(project)
    private val apiIndex: ApiIndex = ApiIndex.getInstance(project)
    private val apiClassRecognizer: CompositeApiClassRecognizer = CompositeApiClassRecognizer.getInstance(project)

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LOG.warn("Uncaught coroutine exception in ApiIndexManager", throwable)
    }

    private var scope = CoroutineScope(SupervisorJob() + IdeDispatchers.Background + exceptionHandler)

    private var lastScanTime = 0L
    private val minScanIntervalMs = 10000L
    private val initialScanDelayMs = 5000L

    /**
     * Conflated channel for full scan requests — multiple requests coalesce into one.
     */
    private var fullScanChannel = Channel<Unit>(CONFLATED)

    /**
     * Unlimited channel for incremental scan requests — each batch of files is processed.
     */
    private var incrementalScanChannel = Channel<List<String>>(Channel.UNLIMITED)

    @Volatile
    private var started = false

    fun start(triggerInitialScan: Boolean = true) {
        if (started) {
            if (triggerInitialScan) {
                scope.launch {
                    delay(initialScanDelayMs)
                    fullScanChannel.trySend(Unit)
                }
            }
            return
        }
        LOG.info("ApiIndexManager starting...")

        // Reinitialize if previously stopped
        if (!scope.isActive) {
            scope = CoroutineScope(SupervisorJob() + IdeDispatchers.Background + exceptionHandler)
            fullScanChannel = Channel(CONFLATED)
            incrementalScanChannel = Channel(Channel.UNLIMITED)
        }

        scope.launch { processFullScans() }
        scope.launch { processIncrementalScans() }
        started = true

        if (triggerInitialScan) {
            scope.launch {
                delay(initialScanDelayMs)
                fullScanChannel.trySend(Unit)
            }
        }
    }

    fun stop() {
        LOG.info("ApiIndexManager stopping...")
        fullScanChannel.close()
        incrementalScanChannel.close()
        scope.cancel()
        started = false
    }

    /**
     * Requests a full scan. Returns immediately.
     */
    fun requestScan() {
        LOG.info("Full scan requested")
        val sent = fullScanChannel.trySend(Unit)
        LOG.info("Full scan request sent: ${sent.isSuccess}")
    }

    /**
     * Reindex files.
     * Called by [ApiFileChangeListener] when source files change.
     */
    suspend fun reIndex(filePaths: List<String>) {
        incrementalScanChannel.send(filePaths)
    }

    private suspend fun processFullScans() {
        for (signal in fullScanChannel) {
            try {
                DumbModeHelper.waitForSmartMode(project)
                LOG.debug("Scanning for API endpoints...")
                val endpoints = apiScanner.scanAll()
                apiIndex.updateEndpoints(endpoints)
                LOG.info("API scan completed, found ${endpoints.size} endpoints")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("API scan failed", e)
            }
        }
    }

    private suspend fun processIncrementalScans() {
        for (filePaths in incrementalScanChannel) {
            // Throttle: skip if last scan was too recent
            val now = System.currentTimeMillis()
            val timeSinceLastScan = now - lastScanTime
            if (timeSinceLastScan < minScanIntervalMs) {
                LOG.debug("Throttling scan, only ${timeSinceLastScan}ms since last scan")
                delay(minScanIntervalMs - timeSinceLastScan)
            }

            // Drain any additional pending batches to coalesce work
            val allFiles = filePaths.toMutableSet()
            while (true) {
                val more = incrementalScanChannel.tryReceive().getOrNull() ?: break
                allFiles.addAll(more)
            }

            try {
                val changedClasses = findClassesFromFiles(allFiles.toList())
                val changedClassNames = read { changedClasses.mapNotNull { it.qualifiedName }.toSet() }

                if (changedClasses.isEmpty()) {
                    LOG.debug("No API classes found in changed files")
                    continue
                }

                LOG.debug("Found ${changedClasses.size} API classes in changed files")

                val newEndpoints = apiScanner.scanClasses(changedClasses).toList()
                val classEndpoints = changedClassNames.associateWith { className ->
                    newEndpoints.filter { it.className == className }
                }

                apiIndex.updateEndpointsByClasses(classEndpoints)
                lastScanTime = System.currentTimeMillis()
                LOG.info("Incremental scan completed, updated ${newEndpoints.size} endpoints from ${changedClasses.size} classes")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("Incremental scan failed, triggering full scan", e)
                fullScanChannel.trySend(Unit)
            }
        }
    }

    private suspend fun findClassesFromFiles(filePaths: List<String>): List<PsiClass> {
        val classes = mutableListOf<PsiClass>()

        // Process in chunks to avoid blocking UI
        filePaths.chunked(10).forEach { chunk ->
            val chunkClasses = read {
                val psiManager = PsiManager.getInstance(project)
                val result = mutableListOf<PsiClass>()

                for (filePath in chunk) {
                    try {
                        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
                        if (virtualFile != null && virtualFile.exists()) {
                            val psiFile = psiManager.findFile(virtualFile)
                            if (psiFile != null) {
                                val fileClasses = psiFile.children.filterIsInstance<PsiClass>()
                                val apiClasses = fileClasses.filter { psiClass ->
                                    isApiClassFast(psiClass)
                                }
                                result.addAll(apiClasses)
                            }
                        }
                    } catch (e: Exception) {
                        LOG.warn("Error finding class for file: $filePath", e)
                    }
                }
                result
            }
            classes.addAll(chunkClasses)
            yield() // Allow other coroutines to run
        }

        return classes
    }

    private fun isApiClassFast(psiClass: PsiClass): Boolean {
        val annotationNames = psiClass.annotations.mapNotNull { it.qualifiedName }
        return annotationNames.any { it in apiClassRecognizer.allTargetAnnotations }
    }

    override fun dispose() {
        stop()
    }

    companion object {
        fun getInstance(project: Project): ApiIndexManager = project.service()
    }
}
