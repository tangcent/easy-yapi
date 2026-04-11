package com.itangcent.easyapi.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.itangcent.easyapi.cache.ApiFileChangeListener
import com.itangcent.easyapi.cache.ApiIndexManager
import com.itangcent.easyapi.cache.VcsBranchChangeListener
import com.itangcent.easyapi.config.ConfigSyncService
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.settings.SettingBinder
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Project activity that initializes the API index services.
 *
 * Migrated from StartupActivity to ProjectActivity for better startup performance
 * and modern coroutine-based initialization.
 *
 * Uses [backgroundAsync] to ensure all downstream PSI operations run on clean
 * background threads without inherited EDT context.
 *
 * @see ApiIndexManager
 * @see ApiFileChangeListener
 */
class ApiIndexStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        backgroundAsync {
            DumbModeHelper.waitForSmartMode(project)

            delay(5.seconds)

            ConfigSyncService.getInstance(project).start()
            ApiFileChangeListener.getInstance(project).start()
            VcsBranchChangeListener.getInstance(project).start()

            delay(10.seconds)

            val settings = SettingBinder.getInstance(project).read()
            val autoScan = settings.autoScanEnabled

            ApiIndexManager.getInstance(project).start(triggerInitialScan = autoScan)
        }
    }
}
