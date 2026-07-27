package com.itangcent.easyapi.core.cache.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.cache.VcsBranchChangeListener
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.settings.SettingsChangeListener
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.settings

/**
 * Bridges settings changes to the lifecycle of the API index services.
 *
 * The startup activity ([com.itangcent.easyapi.core.ide.ApiIndexStartupActivity])
 * only runs once at project open. If `apiScanEnabled` is `false` at that moment,
 * [ApiFileChangeListener], [VcsBranchChangeListener], and [ApiIndexManager] are
 * never started. When the user later flips `apiScanEnabled` to `true` in
 * Settings, this controller starts them on the fly so the dashboard's refresh
 * and auto-scan work without a project restart.
 *
 * Each `start()` call on the underlying services is idempotent (re-subscribes
 * the message bus connection), so this controller can fire on every settings
 * change without double-registering.
 */
@Service(Service.Level.PROJECT)
class ApiScanStartupController(private val project: Project) : Disposable, IdeaLog {

    @Volatile
    private var servicesStarted = false

    fun onSettingsChanged() {
        val apiScanEnabled = project.settings<GeneralSettings>().apiScanEnabled
        if (apiScanEnabled) {
            startIndexServicesIfNeeded()
        }
    }

    private fun startIndexServicesIfNeeded() {
        if (servicesStarted) return
        servicesStarted = true
        LOG.info("apiScanEnabled flipped to true — starting API index services")
        ApiFileChangeListener.getInstance(project).start()
        VcsBranchChangeListener.getInstance(project).start()
        // triggerInitialScan=true so a fresh enable kicks off a scan even if
        // autoScanEnabled is false (the user can still click Refresh).
        ApiIndexManager.getInstance(project).start(triggerInitialScan = true)
    }

    override fun dispose() {
        // Services own their own disposables; nothing to release here.
    }

    companion object {
        fun getInstance(project: Project): ApiScanStartupController = project.service()
    }
}

/**
 * Project-wide listener that forwards settings changes to the
 * [ApiScanStartupController], so the index services start when
 * `apiScanEnabled` is flipped to `true` at runtime.
 */
@Service(Service.Level.PROJECT)
class ApiScanSettingsListener(private val project: Project) : Disposable, IdeaLog {

    init {
        project.messageBus.connect(this).subscribe(
            SettingsChangeListener.TOPIC,
            object : SettingsChangeListener {
                override fun settingsChanged() {
                    ApiScanStartupController.getInstance(project).onSettingsChanged()
                }
            }
        )
    }

    override fun dispose() {
        // connection auto-disposed via connect(this)
    }

    companion object {
        fun getInstance(project: Project): ApiScanSettingsListener = project.service()
    }
}
