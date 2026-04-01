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

    private val scope = CoroutineScope(SupervisorJob() + IdeDispatchers.Background + exceptionHandler)

    /**
     * Conflated channel for full scan requests — multiple requests coalesce into one.
     */
    private val fullScanChannel = Channel<Unit>(CONFLATED)

    /**
     * Unlimited channel for incremental scan requests — each batch of files is processed.
     */
    private val incrementalScanChannel = Channel<List<String>>(Channel.UNLIMITED)

    fun start() {
        LOG.info("ApiIndexManager starting...")

        // Launch the scan processor loops
        scope.launch { processFullScans() }
        scope.launch { processIncrementalScans() }

        // Trigger initial full scan
        fullScanChannel.trySend(Unit)
    }

    fun stop() {
        LOG.info("ApiIndexManager stopping...")
        fullScanChannel.close()
        incrementalScanChannel.close()
        scope.cancel()
    }

    /**
     * Requests a full scan. Returns immediately.
     */
    fun requestScan() {
        LOG.info("Full scan requested")
        fullScanChannel.trySend(Unit)
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
                LOG.info("Incremental scan completed, updated ${newEndpoints.size} endpoints from ${changedClasses.size} classes")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("Incremental scan failed, triggering full scan", e)
                fullScanChannel.trySend(Unit)
            }
        }
    }

    private suspend fun findClassesFromFiles(filePaths: List<String>): List<PsiClass> = read {
        val psiManager = PsiManager.getInstance(project)
        val classes = mutableListOf<PsiClass>()

        for (filePath in filePaths) {
            try {
                val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
                if (virtualFile != null && virtualFile.exists()) {
                    val psiFile = psiManager.findFile(virtualFile)
                    if (psiFile != null) {
                        val fileClasses = psiFile.children.filterIsInstance<PsiClass>()
                        val apiClasses = fileClasses.filter { psiClass ->
                            isApiClassFast(psiClass)
                        }
                        classes.addAll(apiClasses)
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Error finding class for file: $filePath", e)
            }
        }

        classes
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
