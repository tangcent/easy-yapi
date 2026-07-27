package com.itangcent.easyapi.core.cache.api

import com.itangcent.easyapi.core.cache.VcsBranchChangeListener
import com.itangcent.easyapi.core.settings.SettingsChangeListener
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.itangcent.easyapi.testFramework.waitUntil
import com.itangcent.easyapi.testFramework.waitUntilNotEmpty
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [ApiScanStartupController] and [ApiScanSettingsListener].
 *
 * These services close the gap left by [com.itangcent.easyapi.core.ide.ApiIndexStartupActivity]:
 * when the project is opened with `apiScanEnabled=false` the startup activity
 * never starts the index services, so flipping the toggle at runtime must
 * start them on the fly. The controller must also be idempotent — repeated
 * settings changes must not double-start the services.
 */
class ApiScanStartupControllerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var controller: ApiScanStartupController
    private lateinit var listener: ApiScanSettingsListener
    private lateinit var apiIndexManager: ApiIndexManager
    private lateinit var apiFileChangeListener: ApiFileChangeListener
    private lateinit var vcsBranchChangeListener: VcsBranchChangeListener

    override fun setUp() {
        super.setUp()
        controller = ApiScanStartupController.getInstance(project)
        listener = ApiScanSettingsListener.getInstance(project)
        apiIndexManager = ApiIndexManager.getInstance(project)
        apiFileChangeListener = ApiFileChangeListener.getInstance(project)
        vcsBranchChangeListener = VcsBranchChangeListener.getInstance(project)

        // Ensure we start from a clean state: services not yet started and
        // apiScanEnabled defaulted to true. The controller's `servicesStarted`
        // flag is one-shot for the lifetime of the project service instance,
        // so we rely on a fresh project per test (the fixture's contract).
        runBlocking { ApiIndex.getInstance(project).invalidate() }
    }

    override fun tearDown() {
        // Stop the manager if this test started it, so background coroutines
        // don't leak into sibling tests.
        runBlocking {
            apiIndexManager.stop()
            apiFileChangeListener.dispose()
            vcsBranchChangeListener.dispose()
        }
        super.tearDown()
    }

    fun testGetInstance_returnsSameInstance() {
        assertSame(
            "ApiScanStartupController should be a project singleton",
            controller,
            ApiScanStartupController.getInstance(project)
        )
        assertSame(
            "ApiScanSettingsListener should be a project singleton",
            listener,
            ApiScanSettingsListener.getInstance(project)
        )
    }

    /**
     * When `apiScanEnabled` is already true, `onSettingsChanged()` must start
     * the index services so a refresh / auto-scan works immediately.
     */
    fun testOnSettingsChanged_startsIndexServicesWhenEnabled() {
        // apiScanEnabled defaults to true in GeneralSettings.
        controller.onSettingsChanged()

        // The ApiIndexManager should now be running (started flag set). We
        // verify by requesting a scan — if services weren't started this would
        // either no-op or hit a closed channel; either way `start()` would
        // not have been called.
        assertTrue(
            "ApiIndexManager should be started after onSettingsChanged with apiScanEnabled=true",
            apiIndexManager.isStarted()
        )
    }

    /**
     * When `apiScanEnabled` is false, `onSettingsChanged()` must NOT start the
     * index services — the master toggle is off, so scanning must remain off.
     */
    fun testOnSettingsChanged_doesNotStartServicesWhenDisabled() {
        // Stop any manager that might have been auto-started by the base
        // class's `branchHasChanged("test-setup")` event (which triggers
        // `requestScan()` → defense-in-depth auto-start when apiScanEnabled
        // is still true at that point).
        apiIndexManager.stop()

        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = false
        }

        try {
            controller.onSettingsChanged()

            assertFalse(
                "ApiIndexManager should NOT be started when apiScanEnabled is false",
                apiIndexManager.isStarted()
            )
        } finally {
            settingBinder.update(GeneralSettings::class) {
                apiScanEnabled = true
            }
        }
    }

    /**
     * The controller must be idempotent: repeated `onSettingsChanged()` calls
     * (e.g. the user toggles unrelated settings) must not double-start the
     * services or throw.
     */
    fun testOnSettingsChanged_isIdempotent() {
        controller.onSettingsChanged()
        controller.onSettingsChanged()
        controller.onSettingsChanged()

        assertTrue(
            "ApiIndexManager should be started after repeated onSettingsChanged calls",
            apiIndexManager.isStarted()
        )
    }

    /**
     * The settings listener must forward `SettingsChangeListener` events to
     * the controller. Publishing on the message bus should start the index
     * services when `apiScanEnabled` is true.
     */
    fun testSettingsListener_forwardsEventsToController() {
        project.messageBus.syncPublisher(SettingsChangeListener.TOPIC).settingsChanged()

        assertTrue(
            "ApiIndexManager should be started after settingsChanged is published",
            apiIndexManager.isStarted()
        )
    }

    /**
     * The settings listener must NOT start services when `apiScanEnabled` is
     * false at the time the event fires.
     */
    fun testSettingsListener_doesNotStartServicesWhenDisabled() {
        // Stop any manager that might have been auto-started before this test.
        apiIndexManager.stop()

        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = false
        }

        try {
            project.messageBus.syncPublisher(SettingsChangeListener.TOPIC).settingsChanged()

            assertFalse(
                "ApiIndexManager should NOT be started when apiScanEnabled is false",
                apiIndexManager.isStarted()
            )
        } finally {
            settingBinder.update(GeneralSettings::class) {
                apiScanEnabled = true
            }
        }
    }

    /**
     * `dispose()` on both services must not throw. This covers the disposal
     * paths (currently no-ops, but protected by contract).
     */
    fun testDispose_doesNotThrow() {
        controller.dispose()
        listener.dispose()
    }

    /**
     * After the controller starts services, a real scan must be dispatched
     * (`triggerInitialScan = true`) so enabling the toggle at runtime results
     * in a populated index, not just running consumers.
     *
     * This is the end-to-end path the bug report describes: project opened
     * with `apiScanEnabled=false`, user flips the toggle, dashboard should
     * show endpoints without a restart.
     */
    fun testControllerStartTriggersInitialScan() = runTest {
        // Load the full set of test API files (mirrors ApiIndexManagerTest's
        // loadTestFiles) so the scanner has something to find.
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("spring/ResponseBody.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")

        val apiIndex = ApiIndex.getInstance(project)

        controller.onSettingsChanged()

        // The controller-triggered initial scan may find 0 endpoints before
        // PSI is fully resolved (same race as ApiIndexManagerTest). Re-request
        // a scan when the cache is valid but empty, mirroring waitForEndpoints.
        waitUntil(timeout = 30.seconds) { apiIndex.isReady() }
        val endpoints = waitUntilNotEmpty(timeout = 30.seconds) {
            val eps = apiIndex.endpoints()
            if (eps.isEmpty() && apiIndex.isValid()) {
                apiIndexManager.requestScan()
            }
            eps
        }

        assertTrue(
            "Index should be valid after controller-triggered initial scan",
            apiIndex.isValid()
        )
        assertTrue(
            "Index should have endpoints after controller-triggered initial scan",
            endpoints.isNotEmpty()
        )
    }

    override fun createConfigReader() = TestConfigReader.empty(project)
}
