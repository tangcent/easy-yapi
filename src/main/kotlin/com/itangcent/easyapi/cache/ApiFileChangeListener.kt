package com.itangcent.easyapi.cache

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.ide.DumbModeHelper
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Listens for file changes and triggers API index updates.
 *
 * Monitors `.java` and `.kt` file changes and notifies [ApiIndexManager]
 * to reindex the affected files. Uses debouncing to batch rapid changes.
 *
 * ## Features
 * - Automatic file change detection via VFS listener
 * - Debouncing with configurable delay (default: 2 seconds)
 * - Dumb mode awareness (skips during indexing)
 *
 * @see ApiIndexManager for index updates
 */
@Service(Service.Level.PROJECT)
class ApiFileChangeListener(private val project: Project) : BulkFileListener, Disposable, IdeaLog {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LOG.warn("Uncaught coroutine exception in ApiFileChangeListener", throwable)
    }

    private val scope = CoroutineScope(SupervisorJob() + IdeDispatchers.Background + exceptionHandler)

    private val pendingFiles = mutableSetOf<String>()
    private val pendingFilesMutex = Mutex()
    private var debounceJob: Job? = null
    private val throttleDelayMs = 2000L

    fun start() {
        ApplicationManager.getApplication()
            .messageBus
            .connect(this)
            .subscribe(VirtualFileManager.VFS_CHANGES, this)
    }

    override fun after(events: MutableList<out VFileEvent>) {
        if (DumbModeHelper.isDumb(project)) {
            return
        }

        val changedFiles = events
            .mapNotNull { event ->
                val file = event.file ?: return@mapNotNull null
                val fileName = file.name
                if (fileName.endsWith(".java") || fileName.endsWith(".kt")) {
                    file.path
                } else {
                    null
                }
            }
            .filter { it.isNotEmpty() }

        if (changedFiles.isEmpty()) {
            return
        }

        scope.launch {
            pendingFilesMutex.withLock {
                pendingFiles.addAll(changedFiles)
            }
        }

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(throttleDelayMs)
            processPendingChanges()
        }
    }

    private suspend fun processPendingChanges() {
        val filesToProcess = pendingFilesMutex.withLock {
            if (pendingFiles.isEmpty()) {
                return
            }
            val files = pendingFiles.toList()
            pendingFiles.clear()
            files
        }

        LOG.debug("Processing ${filesToProcess.size} changed files")
        ApiIndexManager.getInstance(project).reIndex(filesToProcess)
    }

    override fun dispose() {
        debounceJob?.cancel()
        scope.cancel()
    }

    companion object {
        fun getInstance(project: Project): ApiFileChangeListener = project.service()
    }
}
