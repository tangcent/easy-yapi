package com.itangcent.easyapi.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.itangcent.easyapi.cache.ApiFileChangeListener
import com.itangcent.easyapi.cache.ApiIndexManager
import com.itangcent.easyapi.cache.VcsBranchChangeListener
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.settings.SettingBinder

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
            val settings = SettingBinder.getInstance(project).read()
            val autoScan = settings.autoScanEnabled

            ApiFileChangeListener.getInstance(project).start()
            ApiIndexManager.getInstance(project).start(triggerInitialScan = autoScan)
            VcsBranchChangeListener.getInstance(project).start()
        }
    }
}
