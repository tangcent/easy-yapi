package com.itangcent.easyapi.ide.search

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.swing.JList
import javax.swing.JPanel

class ApiSearchResultRendererTest {

    private lateinit var renderer: ApiSearchResultRenderer
    private lateinit var mockList: JList<ApiEndpoint>

    @Before
    fun setUp() {
        renderer = ApiSearchResultRenderer()
        mockList = JList()
    }

    @Test
    fun testRendererCreatesComponent() {
        val endpoint = createEndpoint(
            name = "getUser",
            path = "/api/users/{id}",
            method = HttpMethod.GET
        )

        val component = renderer.getListCellRendererComponent(
            mockList, endpoint, 0, false, false
        )

        assertNotNull("Renderer should create a component", component)
        assertTrue("Component should be a JPanel", component is JPanel)
    }

    @Test
    fun testRendererWithGetMethod() {
        val endpoint = createEndpoint(
            name = "getUser",
            path = "/api/users/{id}",
            method = HttpMethod.GET
        )

        val component = renderer.getListCellRendererComponent(
            mockList, endpoint, 0, false, false
        )

        assertNotNull("Renderer should handle GET method", component)
    }

    @Test
    fun testRendererWithPostMethod() {
        val endpoint = createEndpoint(
            name = "createUser",
            path = "/api/users",
            method = HttpMethod.POST
        )

        val component = renderer.getListCellRendererComponent(
            mockList, endpoint, 0, false, false
        )

        assertNotNull("Renderer should handle POST method", component)
    }

    @Test
    fun testRendererWithPutMethod() {
        val endpoint = createEndpoint(
            name = "updateUser",
            path = "/api/users/{id}",
            method = HttpMethod.PUT
        )

        val component = renderer.getListCellRendererComponent(
            mockList, endpoint, 0, false, false
        )

        assertNotNull("Renderer should handle PUT method", component)
    }

    @Test
    fun testRendererWithDeleteMethod() {
        val endpoint = createEndpoint(
            name = "deleteUser",
            path = "/api/users/{id}",
            method = HttpMethod.DELETE
        )

        val component = renderer.getListCellRendererComponent(
            mockList, endpoint, 0, false, false
        )

        assertNotNull("Renderer should handle DELETE method", component)
    }

    @Test
    fun testRendererWithPatchMethod() {
        val endpoint = createEndpoint(
            name = "patchUser",
            path = "/api/users/{id}",
            method = HttpMethod.PATCH
        )

        val component = renderer.getListCellRendererComponent(
            mockList, endpoint, 0, false, false
        )

        assertNotNull("Renderer should handle PATCH method", component)
    }

    @Test
    fun testRendererWithNoName() {
        val endpoint = createEndpoint(
            name = null,
            path = "/api/users",
            method = HttpMethod.GET
        )

        val component = renderer.getListCellRendererComponent(
            mockList, endpoint, 0, false, false
        )

        assertNotNull("Renderer should handle endpoint without name", component)
    }

    @Test
    fun testRendererWithNoClassName() {
        val endpoint = createEndpoint(
            name = "getUser",
            path = "/api/users/{id}",
            method = HttpMethod.GET,
            className = null
        )

        val component = renderer.getListCellRendererComponent(
            mockList, endpoint, 0, false, false
        )

        assertNotNull("Renderer should handle endpoint without class name", component)
    }

    @Test
    fun testRendererWithSelection() {
        val endpoint = createEndpoint(
            name = "getUser",
            path = "/api/users/{id}",
            method = HttpMethod.GET
        )

        val component = renderer.getListCellRendererComponent(
            mockList, endpoint, 0, true, false
        )

        assertNotNull("Renderer should handle selected state", component)
    }

    @Test
    fun testRendererWithFocus() {
        val endpoint = createEndpoint(
            name = "getUser",
            path = "/api/users/{id}",
            method = HttpMethod.GET
        )

        val component = renderer.getListCellRendererComponent(
            mockList, endpoint, 0, false, true
        )

        assertNotNull("Renderer should handle focus state", component)
    }

    @Test
    fun testRendererWithHeadMethod() {
        val endpoint = createEndpoint(
            name = "headUser",
            path = "/api/users/{id}",
            method = HttpMethod.HEAD
        )

        val component = renderer.getListCellRendererComponent(
            mockList, endpoint, 0, false, false
        )

        assertNotNull("Renderer should handle HEAD method", component)
    }

    @Test
    fun testRendererWithOptionsMethod() {
        val endpoint = createEndpoint(
            name = "optionsUser",
            path = "/api/users",
            method = HttpMethod.OPTIONS
        )

        val component = renderer.getListCellRendererComponent(
            mockList, endpoint, 0, false, false
        )

        assertNotNull("Renderer should handle OPTIONS method", component)
    }

    private fun createEndpoint(
        name: String?,
        path: String,
        method: HttpMethod,
        className: String? = "com.example.UserController"
    ): ApiEndpoint {
        return ApiEndpoint(
            name = name,
            className = className,
            metadata = httpMetadata(
                path = path,
                method = method
            )
        )
    }
}
