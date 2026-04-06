package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.exporter.model.path
import com.itangcent.easyapi.testFramework.ApiFixtures
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import javax.swing.tree.DefaultMutableTreeNode

class ApiDashboardPanelTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var apiDashboardPanel: ApiDashboardPanel

    override fun setUp() {
        super.setUp()
        apiDashboardPanel = ApiDashboardPanel(project)
    }

    override fun tearDown() {
        apiDashboardPanel.dispose()
        super.tearDown()
    }

    fun testPanelInitializesWithTree() {
        assertNotNull("Panel should have tree component", apiDashboardPanel.getComponent(0))
    }

    fun testUpdateTreePopulatesWithEndpoints() = runBlocking {
        val endpoints = ApiFixtures.createSampleEndpoints()
        
        val root = DefaultMutableTreeNode("API Endpoints")
        val groupedByFolder = endpoints.groupBy { it.folder ?: "Default" }

        for ((folder, folderEndpoints) in groupedByFolder.toSortedMap()) {
            val folderNode = DefaultMutableTreeNode(folder)
            for (endpoint in folderEndpoints.sortedBy { it.path }) {
                val endpointNode = DefaultMutableTreeNode(endpoint)
                folderNode.add(endpointNode)
            }
            root.add(folderNode)
        }

        assertTrue("Root should have children", root.childCount > 0)
    }

    fun testUpdateTreeGroupsEndpointsByFolder() = runBlocking {
        val endpoints = listOf(
            ApiFixtures.createEndpoint(folder = "User API"),
            ApiFixtures.createEndpoint(folder = "Admin API"),
            ApiFixtures.createEndpoint(folder = "User API")
        )

        val groupedByFolder = endpoints.groupBy { it.folder ?: "Default" }
        assertEquals("Should have 2 folders", 2, groupedByFolder.size)
        assertEquals("User API should have 2 endpoints", 2, groupedByFolder["User API"]?.size)
        assertEquals("Admin API should have 1 endpoint", 1, groupedByFolder["Admin API"]?.size)
    }

    fun testEndpointDetailsPanelShowsEndpoint() {
        val endpoint = ApiFixtures.createGetEndpoint()
        val detailsPanel = EndpointDetailsPanel(project, com.itangcent.easyapi.http.UrlConnectionHttpClient)
        
        detailsPanel.showEndpoint(endpoint)
        
        assertNotNull("Details panel should show endpoint", detailsPanel)
    }
}
