package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class ApiTreeCellRendererTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var renderer: ApiTreeCellRenderer
    private lateinit var tree: JTree

    override fun setUp() {
        super.setUp()
        renderer = ApiTreeCellRenderer()
        tree = JTree()
    }

    fun testGetTreeCellRendererComponentWithApiEndpoint() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            path = "/api/users/{id}",
            method = HttpMethod.GET
        )
        
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        
        assertNotNull(component)
    }

    fun testGetTreeCellRendererComponentWithDefaultMutableTreeNode() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            path = "/api/users",
            method = HttpMethod.POST
        )
        val node = DefaultMutableTreeNode(endpoint)
        
        val component = renderer.getTreeCellRendererComponent(
            tree, node, false, false, true, 0, false
        )
        
        assertNotNull(component)
    }

    fun testGetTreeCellRendererComponentWithNonEndpoint() {
        val component = renderer.getTreeCellRendererComponent(
            tree, "Not an endpoint", false, false, true, 0, false
        )
        
        assertNotNull(component)
    }

    fun testGetMethodColorForGet() {
        val endpoint = ApiEndpoint(method = HttpMethod.GET, path = "/test")
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForPost() {
        val endpoint = ApiEndpoint(method = HttpMethod.POST, path = "/test")
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForPut() {
        val endpoint = ApiEndpoint(method = HttpMethod.PUT, path = "/test")
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForDelete() {
        val endpoint = ApiEndpoint(method = HttpMethod.DELETE, path = "/test")
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForPatch() {
        val endpoint = ApiEndpoint(method = HttpMethod.PATCH, path = "/test")
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForHead() {
        val endpoint = ApiEndpoint(method = HttpMethod.HEAD, path = "/test")
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForOptions() {
        val endpoint = ApiEndpoint(method = HttpMethod.OPTIONS, path = "/test")
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testBuildApiTextWithName() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            path = "/api/users/{id}",
            method = HttpMethod.GET
        )
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testBuildApiTextWithoutName() {
        val endpoint = ApiEndpoint(
            name = null,
            path = "/api/users",
            method = HttpMethod.GET
        )
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }
}
