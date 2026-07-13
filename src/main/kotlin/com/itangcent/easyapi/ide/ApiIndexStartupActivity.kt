package com.itangcent.easyapi.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.itangcent.easyapi.cache.api.ApiFileChangeListener
import com.itangcent.easyapi.cache.api.ApiIndexManager
import com.itangcent.easyapi.cache.VcsBranchChangeListener
import com.itangcent.easyapi.config.ConfigSyncService
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.settings.module.GeneralSettings
import com.itangcent.easyapi.settings.settings
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
 * **Test mode:** This activity is a no-op when `ApplicationManager.isUnitTestMode`
 * is true. Tests explicitly control the [ApiIndexManager] lifecycle (setUp/tearDown),
 * and the delayed background initialization (5s + 10s delays) would leak coroutines
 * on the singleton `IdeDispatchers.scope` that outlive disposed test projects,
 * causing `AlreadyDisposedException` on CI where the delays elapse during later tests.
 *
 * @see ApiIndexManager
 * @see ApiFileChangeListener
 */
class ApiIndexStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) return

        backgroundAsync {
            DumbModeHelper.waitForSmartMode(project)

            delay(5.seconds)

            if (project.isDisposed) return@backgroundAsync

            ConfigSyncService.getInstance(project).start()
            ApiFileChangeListener.getInstance(project).start()
            VcsBranchChangeListener.getInstance(project).start()

            delay(10.seconds)

            if (project.isDisposed) return@backgroundAsync

            val autoScan = project.settings<GeneralSettings>().autoScanEnabled

            ApiIndexManager.getInstance(project).start(triggerInitialScan = autoScan)
        }
    }
}
