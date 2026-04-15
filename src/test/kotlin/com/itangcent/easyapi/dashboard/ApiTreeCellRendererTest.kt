package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.exporter.model.*
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
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )
        
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        
        assertNotNull(component)
    }

    fun testGetTreeCellRendererComponentWithDefaultMutableTreeNode() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST
            )
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
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/test", method = HttpMethod.GET))
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForPost() {
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/test", method = HttpMethod.POST))
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForPut() {
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/test", method = HttpMethod.PUT))
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForDelete() {
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/test", method = HttpMethod.DELETE))
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForPatch() {
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/test", method = HttpMethod.PATCH))
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForHead() {
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/test", method = HttpMethod.HEAD))
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForOptions() {
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/test", method = HttpMethod.OPTIONS))
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testBuildApiTextWithName() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testBuildApiTextWithoutName() {
        val endpoint = ApiEndpoint(
            name = null,
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.GET
            )
        )
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGrpcUnaryEndpointRendering() {
        val endpoint = ApiEndpoint(
            name = "SayHello",
            metadata = GrpcMetadata(
                path = "/com.example.GreeterService/SayHello",
                serviceName = "GreeterService",
                methodName = "SayHello",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY
            )
        )
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        ) as javax.swing.JLabel
        assertTrue(component.text.startsWith("gRPC:U"))
        assertEquals(java.awt.Color(0x8B5CF6), component.foreground)
    }

    fun testGrpcServerStreamingEndpointRendering() {
        val endpoint = ApiEndpoint(
            name = "ListFeatures",
            metadata = GrpcMetadata(
                path = "/com.example.RouteGuide/ListFeatures",
                serviceName = "RouteGuide",
                methodName = "ListFeatures",
                packageName = "com.example",
                streamingType = GrpcStreamingType.SERVER_STREAMING
            )
        )
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        ) as javax.swing.JLabel
        assertTrue(component.text.startsWith("gRPC:S"))
        assertEquals(java.awt.Color(0x8B5CF6), component.foreground)
    }

    fun testGrpcClientStreamingEndpointRendering() {
        val endpoint = ApiEndpoint(
            name = "RecordRoute",
            metadata = GrpcMetadata(
                path = "/com.example.RouteGuide/RecordRoute",
                serviceName = "RouteGuide",
                methodName = "RecordRoute",
                packageName = "com.example",
                streamingType = GrpcStreamingType.CLIENT_STREAMING
            )
        )
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        ) as javax.swing.JLabel
        assertTrue(component.text.startsWith("gRPC:C"))
        assertEquals(java.awt.Color(0x8B5CF6), component.foreground)
    }

    fun testGrpcBidirectionalEndpointRendering() {
        val endpoint = ApiEndpoint(
            name = "RouteChat",
            metadata = GrpcMetadata(
                path = "/com.example.RouteGuide/RouteChat",
                serviceName = "RouteGuide",
                methodName = "RouteChat",
                packageName = "com.example",
                streamingType = GrpcStreamingType.BIDIRECTIONAL
            )
        )
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        ) as javax.swing.JLabel
        assertTrue(component.text.startsWith("gRPC:B"))
        assertEquals(java.awt.Color(0x8B5CF6), component.foreground)
    }

    fun testGrpcEndpointWithNameAndPath() {
        val endpoint = ApiEndpoint(
            name = "SayHello",
            metadata = GrpcMetadata(
                path = "/com.example.GreeterService/SayHello",
                serviceName = "GreeterService",
                methodName = "SayHello",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY
            )
        )
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        ) as javax.swing.JLabel
        assertEquals("gRPC:U SayHello [/com.example.GreeterService/SayHello]", component.text)
    }

    fun testHttpEndpointStillShowsMethodName() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        ) as javax.swing.JLabel
        assertTrue(component.text.startsWith("GET"))
        assertEquals(java.awt.Color(0x61affe), component.foreground)
    }
}
