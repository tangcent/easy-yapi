package com.itangcent.easyapi.config

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.itangcent.easyapi.config.ConfigSyncService.Companion.DEBOUNCE_DELAY_MS
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.settings.SettingsChangeListener
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

/**
 * Service that synchronizes configuration changes by automatically reloading
 * the [ConfigReader] when relevant changes are detected.
 *
 * This service monitors two types of changes:
 * 1. **File changes**: Changes to `.easy.api.config*` files in the project
 * 2. **Settings changes**: Changes to plugin settings via [SettingsChangeListener]
 *
 * ## Debounce Behavior
 *
 * Configuration reloads are debounced with a 300ms delay to prevent excessive
 * reload operations when multiple file change events occur in rapid succession
 * (e.g., during bulk file operations or IDE indexing).
 *
 * The debounce mechanism works by:
 * - Canceling any pending reload job when a new change is detected
 * - Launching a new coroutine that delays before executing the reload
 * - Only executing the reload if no further changes occur during the delay
 *
 * This is a project-level service scoped to a specific IntelliJ project.
 * The service lifecycle is managed by the IntelliJ platform and it implements
 * [Disposable] to clean up resources when the project is closed.
 *
 * @see ConfigReader
 * @see SettingsChangeListener
 */
@Service(Service.Level.PROJECT)
class ConfigSyncService(
    private val project: Project
) : Disposable {

    private val configReader: ConfigReader get() = ConfigReader.getInstance(project)

    private val connection = project.messageBus.connect(this)

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LOG.warn("Uncaught coroutine exception in ConfigSyncService", throwable)
    }

    /**
     * Coroutine scope for managing debounce jobs.
     * Uses SupervisorJob to prevent cascade failures and IdeDispatchers.Background
     * for proper IntelliJ platform thread handling.
     */
    private val scope = CoroutineScope(SupervisorJob() + IdeDispatchers.Background + exceptionHandler)

    /**
     * Current debounce job, cancelled and replaced on each schedule request.
     */
    private var debounceJob: Job? = null

    companion object : IdeaLog {
        fun getInstance(project: Project): ConfigSyncService =
            project.getService(ConfigSyncService::class.java)

        /**
         * Debounce delay that prevents excessive reloads when multiple file changes occur rapidly.
         */
        private val DEBOUNCE_DELAY_MS = 2.seconds
    }

    /**
     * Starts monitoring configuration changes.
     *
     * This method subscribes to:
     * - Virtual File System changes for `.easy.api.config*` files
     * - Settings changes for plugin configuration updates
     *
     * Also performs an initial configuration reload on startup.
     */
    fun start() {
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                onFileChanges(events)
            }
        })

        connection.subscribe(SettingsChangeListener.TOPIC, object : SettingsChangeListener {
            override fun settingsChanged() {
                LOG.info("Settings changed, triggering config reload")
                scheduleReload()
            }
        })

        scope.launch { configReader.reload() }
        LOG.info("ConfigSyncService started")
    }

    /**
     * Handles file change events from the Virtual File System.
     *
     * Checks if any of the changed files match the config file pattern
     * (`.easy.api.config*`) and schedules a debounced reload if so.
     *
     * @param events List of file change events to process
     */
    private fun onFileChanges(events: List<VFileEvent>) {
        val configFileChanged = events.any { event ->
            event.file?.name?.startsWith(".easy.api.config") == true
        }
        if (configFileChanged) {
            LOG.info("Config file changed, triggering reload")
            scheduleReload()
        }
    }

    /**
     * Schedules a debounced configuration reload.
     *
     * This method cancels any existing debounce job and launches a new one
     * that will execute the reload after [DEBOUNCE_DELAY_MS] milliseconds.
     *
     * The debounce mechanism ensures that rapid successive change events
     * result in only a single reload operation.
     */
    private fun scheduleReload() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_DELAY_MS)
            LOG.info("Executing debounced config reload")
            configReader.reload()
        }
    }

    /**
     * Cleans up resources when the service is disposed.
     *
     * Cancels the coroutine scope and disposes the message bus connection.
     */
    override fun dispose() {
        scope.cancel()
        connection.dispose()
    }
}
