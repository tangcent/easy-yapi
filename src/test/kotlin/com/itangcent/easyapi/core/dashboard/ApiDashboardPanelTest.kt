package com.itangcent.easyapi.core.dashboard

import com.itangcent.easyapi.core.cache.api.ApiScanFeatureStateProvider
import com.itangcent.easyapi.core.cache.api.ApiScanFeatureTarget
import com.itangcent.easyapi.core.cache.api.ApiScanLifecycleController
import com.itangcent.easyapi.core.cache.api.ApiScanLifecycleResources
import com.itangcent.easyapi.core.cache.api.ApiScanLifecycleSnapshot
import com.itangcent.easyapi.core.cache.api.ApiScanLifecycleState
import com.itangcent.easyapi.core.cache.api.ApiScanResult
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.internal.threading.swing
import com.itangcent.easyapi.core.internal.threading.swingBlocking
import com.itangcent.easyapi.testFramework.ApiFixtures
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers

class ApiDashboardPanelTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val panels = mutableListOf<ApiDashboardPanel>()
    private val controllers = mutableListOf<ApiScanLifecycleController>()

    override fun tearDown() {
        swingBlocking {
            panels.forEach(ApiDashboardPanel::dispose)
            panels.clear()
        }
        controllers.forEach(ApiScanLifecycleController::dispose)
        controllers.clear()
        super.tearDown()
    }

    fun testDisabledRetainedSnapshotIsDisplayedImmediately() {
        val retained = listOf(ApiFixtures.createEndpoint(name = "retained", path = "/retained"))
        val runtime = FakeDashboardRuntime(retained, scanningEffective = false)

        val panel = createPanel(runtime)

        assertEquals(
            "Disabled dashboard should immediately display retained endpoints",
            retained,
            swingBlocking { panel.displayedEndpoints() }
        )
        assertTrue(
            "Disabled dashboard should explain that scanning is paused",
            swingBlocking { panel.dashboardStatusText() }.startsWith("Paused")
        )
        assertEquals("Initial load should read one non-blocking snapshot", 1, runtime.snapshotReads)
    }

    fun testManualRefreshKeepsOldSnapshotVisibleUntilSuccess() = runTest {
        val retained = listOf(ApiFixtures.createEndpoint(name = "old", path = "/old"))
        val refreshed = listOf(ApiFixtures.createEndpoint(name = "new", path = "/new"))
        val pending = CompletableDeferred<ApiScanResult>()
        val runtime = FakeDashboardRuntime(retained, scanningEffective = false).apply {
            manualWork = pending
        }
        val panel = createPanel(runtime)

        val completion = swing { panel.refresh() }

        assertFalse("Refresh should not block the EDT while scan work is pending", completion.isCompleted)
        assertEquals(
            "Retained content should remain visible while scanning",
            retained,
            swing { panel.displayedEndpoints() }
        )
        assertTrue(
            "Pending refresh should expose a scanning state",
            swing { panel.dashboardStatusText() }.startsWith("Scanning")
        )

        runtime.snapshot = refreshed
        pending.complete(ApiScanResult.Success(1L, refreshed.size))
        val result = completion.await()

        assertTrue("Manual refresh should expose success", result is ApiScanResult.Success)
        assertEquals("Successful refresh should read the retained result", refreshed, swing { panel.displayedEndpoints() })
        assertTrue(
            "Disabled scanning should return to paused after one-shot success",
            swing { panel.dashboardStatusText() }.startsWith("Paused")
        )
        assertEquals("Refresh should delegate exactly once", 1, runtime.manualRefreshCalls)
    }

    fun testFailedManualRefreshKeepsOldUiAndCache() = runTest {
        val retained = listOf(ApiFixtures.createEndpoint(name = "old", path = "/old"))
        val failure = IllegalStateException("controlled refresh failure")
        val runtime = FakeDashboardRuntime(retained, scanningEffective = false).apply {
            manualWork = completed(ApiScanResult.Failed(2L, failure))
        }
        val panel = createPanel(runtime)

        val result = swing { panel.refresh() }.await()

        assertTrue("Refresh failure should remain observable", result is ApiScanResult.Failed)
        assertEquals("Failed refresh must keep displayed endpoints", retained, swing { panel.displayedEndpoints() })
        assertEquals("Failed refresh must not mutate retained cache", retained, runtime.snapshot)
        assertTrue(
            "Failed disabled refresh should describe the retained snapshot as stale",
            swing { panel.dashboardStatusText() }.contains("stale", ignoreCase = true)
        )
    }

    fun testDisabledManualRefreshDoesNotRestoreContinuousListeners() = runTest {
        val resources = ManualRefreshResources()
        val controller = ApiScanLifecycleController(
            project = project,
            stateProvider = ApiScanFeatureStateProvider { DISABLED_TARGET },
            resources = resources,
            dispatcher = Dispatchers.Unconfined
        ).also(controllers::add)
        controller.reconcileInitial().await()

        val retained = listOf(ApiFixtures.createEndpoint(name = "retained", path = "/retained"))
        val panel = createPanel(ControllerDashboardRuntime(retained, controller))

        val result = swing { panel.refresh() }.await()

        assertTrue("Disabled manual refresh should complete as a one-shot", result is ApiScanResult.Success)
        assertEquals("Exactly one one-shot scan should run", 1, resources.oneShotScans)
        assertEquals("Manual refresh must not start the continuous manager", 0, resources.managerStarts)
        assertEquals("Manual refresh must not connect VFS", 0, resources.vfsStarts)
        assertEquals("Manual refresh must not connect VCS", 0, resources.vcsStarts)
        assertEquals(
            "Controller should return to stopped after the one-shot",
            ApiScanLifecycleState.STOPPED,
            controller.snapshot().state
        )
    }

    fun testSearchSharesTheMatchingRulesWithSearchEverywhere() {
        val getUser = ApiFixtures.createEndpoint(name = "获取用户信息", path = "/api/user/get")
        val createUser = ApiFixtures.createEndpoint(
            name = "createUser",
            path = "/api/user/add",
            method = HttpMethod.POST
        )
        val panel = createPanel(FakeDashboardRuntime(listOf(getUser, createUser), scanningEffective = false))

        swingBlocking {
            assertEquals(
                "A method prefix should filter by HTTP method",
                listOf(getUser),
                panel.endpointsMatching("GET /api/user/get")
            )
            assertEquals(
                "Tokens may land on different fields",
                listOf(getUser),
                panel.endpointsMatching("user 用户")
            )
            assertEquals(
                "One token may span the path and the name as a subsequence (#1460)",
                listOf(getUser),
                panel.endpointsMatching("aus用户")
            )
            assertEquals(
                "The same query written with a space matches too",
                listOf(getUser),
                panel.endpointsMatching("aus 用户")
            )
            assertEquals(
                "A pasted URL should match its endpoint",
                listOf(getUser),
                panel.endpointsMatching("http://localhost:8080/api/user/get")
            )
            assertEquals(
                "Unrelated text should match nothing",
                emptyList<ApiEndpoint>(),
                panel.endpointsMatching("orders")
            )
        }
    }

    private fun createPanel(runtime: ApiDashboardRuntime): ApiDashboardPanel = swingBlocking {
        ApiDashboardPanel(project, runtime, Dispatchers.Unconfined).also(panels::add)
    }

    private class FakeDashboardRuntime(
        initialSnapshot: List<ApiEndpoint>,
        private var scanningEffective: Boolean
    ) : ApiDashboardRuntime {
        var snapshot: List<ApiEndpoint> = initialSnapshot
        var snapshotReads: Int = 0
        var manualRefreshCalls: Int = 0
        var manualWork: Deferred<ApiScanResult> = completed(ApiScanResult.Success(1L, initialSnapshot.size))
        private var listener: (suspend (List<ApiEndpoint>) -> Unit)? = null

        override fun retainedSnapshot(): List<ApiEndpoint> {
            snapshotReads++
            return snapshot.toList()
        }

        override fun subscribe(listener: suspend (List<ApiEndpoint>) -> Unit) {
            this.listener = listener
        }

        override fun isScanningEffective(): Boolean = scanningEffective

        override fun lifecycleSnapshot(): ApiScanLifecycleSnapshot = ApiScanLifecycleSnapshot(
            state = ApiScanLifecycleState.STOPPED,
            target = DISABLED_TARGET,
            revision = 0L,
            generation = 1L
        )

        override fun manualRefresh(): Deferred<ApiScanResult> {
            manualRefreshCalls++
            return manualWork
        }
    }

    private class ControllerDashboardRuntime(
        private val snapshot: List<ApiEndpoint>,
        private val controller: ApiScanLifecycleController
    ) : ApiDashboardRuntime {
        override fun retainedSnapshot(): List<ApiEndpoint> = snapshot.toList()

        override fun subscribe(listener: suspend (List<ApiEndpoint>) -> Unit) = Unit

        override fun isScanningEffective(): Boolean = false

        override fun lifecycleSnapshot(): ApiScanLifecycleSnapshot = controller.snapshot()

        override fun manualRefresh(): Deferred<ApiScanResult> = controller.manualRefresh()
    }

    private class ManualRefreshResources : ApiScanLifecycleResources {
        var managerStarts = 0
        var vfsStarts = 0
        var vcsStarts = 0
        var oneShotScans = 0

        override suspend fun startManager(triggerInitialScan: Boolean): Long {
            managerStarts++
            return 1L
        }

        override fun startVfs(generation: Long) {
            vfsStarts++
        }

        override fun startVcs(generation: Long) {
            vcsStarts++
        }

        override fun stopVfs() = Unit

        override fun stopVcs() = Unit

        override suspend fun stopManager(): Long = 1L

        override fun requestFull(generation: Long, source: String): Deferred<ApiScanResult> =
            completed(ApiScanResult.Success(generation, 0))

        override fun requestIncremental(
            generation: Long,
            filePaths: List<String>,
            source: String
        ): Deferred<ApiScanResult> = completed(ApiScanResult.Success(generation, 0))

        override fun runOneShotFull(source: String): Deferred<ApiScanResult> {
            oneShotScans++
            return completed(ApiScanResult.Success(1L, 1))
        }

        override fun stopImmediately() = Unit
    }

    companion object {
        private val DISABLED_TARGET = ApiScanFeatureTarget(
            scanningEnabled = false,
            autoScanningEnabled = false,
            editorIntegrationEnabled = false
        )

        private fun completed(result: ApiScanResult): Deferred<ApiScanResult> =
            CompletableDeferred<ApiScanResult>().apply { complete(result) }
    }
}
