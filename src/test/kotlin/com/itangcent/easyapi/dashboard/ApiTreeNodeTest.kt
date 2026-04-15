package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.exporter.model.*
import org.junit.Assert.*
import org.junit.Test

class ApiTreeNodeTest {

    @Test
    fun testModuleNode() {
        val node = ApiTreeNode.ModuleNode("user-service")
        assertEquals("user-service", node.name)
        assertTrue(node.children.isEmpty())
    }

    @Test
    fun testModuleNode_withChildren() {
        val child = ApiTreeNode.ClassNode("UserController")
        val node = ApiTreeNode.ModuleNode("user-service", listOf(child))
        assertEquals(1, node.children.size)
        assertTrue(node.children[0] is ApiTreeNode.ClassNode)
    }

    @Test
    fun testClassNode() {
        val node = ApiTreeNode.ClassNode("UserController", "User management")
        assertEquals("UserController", node.name)
        assertEquals("User management", node.description)
        assertTrue(node.children.isEmpty())
    }

    @Test
    fun testClassNode_nullDescription() {
        val node = ApiTreeNode.ClassNode("Ctrl")
        assertNull(node.description)
    }

    @Test
    fun testEndpointNode() {
        val endpoint = ApiEndpoint(
            name = "getUsers",
            metadata = httpMetadata(path = "/users", method = HttpMethod.GET)
        )
        val node = ApiTreeNode.EndpointNode(endpoint)
        assertEquals("getUsers", node.endpoint.name)
    }

    @Test
    fun testNestedTree() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/users/{id}", method = HttpMethod.GET)
        )
        val tree = ApiTreeNode.ModuleNode(
            "api",
            listOf(
                ApiTreeNode.ClassNode(
                    "UserController",
                    "Users",
                    listOf(ApiTreeNode.EndpointNode(endpoint))
                )
            )
        )
        val classNode = tree.children[0] as ApiTreeNode.ClassNode
        assertEquals(1, classNode.children.size)
        val ep = classNode.children[0] as ApiTreeNode.EndpointNode
        assertEquals("getUser", ep.endpoint.name)
    }

    @Test
    fun testEquality() {
        val a = ApiTreeNode.ModuleNode("m")
        val b = ApiTreeNode.ModuleNode("m")
        assertEquals(a, b)
    }

    @Test
    fun testSealed_isInstance() {
        val module: ApiTreeNode = ApiTreeNode.ModuleNode("m")
        val cls: ApiTreeNode = ApiTreeNode.ClassNode("c")
        assertTrue(module is ApiTreeNode.ModuleNode)
        assertTrue(cls is ApiTreeNode.ClassNode)
    }
}
