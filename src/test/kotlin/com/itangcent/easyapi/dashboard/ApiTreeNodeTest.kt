package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import org.junit.Assert.*
import org.junit.Test

class ApiTreeNodeTest {

    @Test
    fun testModuleNodeDefaults() {
        val node = ApiTreeNode.ModuleNode("test-module")
        assertEquals("test-module", node.name)
        assertTrue("Children should default to empty", node.children.isEmpty())
    }

    @Test
    fun testModuleNodeWithChildren() {
        val endpoint = ApiEndpoint(name = "test", metadata = HttpMetadata(method = HttpMethod.GET, path = "/test"))
        val child = ApiTreeNode.EndpointNode(endpoint)
        val node = ApiTreeNode.ModuleNode("module", listOf(child))
        assertEquals(1, node.children.size)
    }

    @Test
    fun testClassNodeDefaults() {
        val node = ApiTreeNode.ClassNode("UserController")
        assertEquals("UserController", node.name)
        assertNull("Description should default to null", node.description)
        assertTrue("Children should default to empty", node.children.isEmpty())
    }

    @Test
    fun testClassNodeWithDescription() {
        val node = ApiTreeNode.ClassNode("UserController", "User management")
        assertEquals("User management", node.description)
    }

    @Test
    fun testEndpointNode() {
        val endpoint = ApiEndpoint(name = "Get Users", metadata = HttpMetadata(method = HttpMethod.GET, path = "/api/users"))
        val node = ApiTreeNode.EndpointNode(endpoint)
        assertEquals("Get Users", node.endpoint.name)
    }

    @Test
    fun testTreeHierarchy() {
        val endpoint = ApiEndpoint(
            name = "Get Users",
            metadata = HttpMetadata(method = HttpMethod.GET, path = "/api/users")
        )
        val endpointNode = ApiTreeNode.EndpointNode(endpoint)
        val classNode = ApiTreeNode.ClassNode("UserController", children = listOf(endpointNode))
        val moduleNode = ApiTreeNode.ModuleNode("user-service", children = listOf(classNode))

        assertEquals(1, moduleNode.children.size)
        val classChild = moduleNode.children[0] as ApiTreeNode.ClassNode
        assertEquals(1, classChild.children.size)
        val endpointChild = classChild.children[0] as ApiTreeNode.EndpointNode
        assertEquals("Get Users", endpointChild.endpoint.name)
    }
}
