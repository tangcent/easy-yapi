package com.itangcent.easyapi.cache

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.path
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.wrap
import kotlinx.coroutines.runBlocking

class ApiIndexTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var apiIndex: ApiIndex
    private lateinit var wrappedProject: com.intellij.openapi.project.Project

    override fun setUp() {
        super.setUp()
        apiIndex = ApiIndex()
        wrappedProject = wrap(project) {
            replaceService(ApiIndex::class, apiIndex)
        }
    }

    fun testGetInstance() {
        assertNotNull(ApiIndex.getInstance(wrappedProject))
        assertSame(apiIndex, ApiIndex.getInstance(wrappedProject))
    }

    fun testInitialIsValid() {
        assertFalse(apiIndex.isValid())
    }

    fun testInitialIsReady() {
        assertFalse(apiIndex.isReady())
    }

    fun testInvalidate() {
        runBlocking {
            val endpoint = ApiEndpoint(
                name = "getUser",
                metadata = HttpMetadata(
                    path = "/api/users/{id}",
                    method = HttpMethod.GET
                ),
                className = "com.example.UserController"
            )
            apiIndex.updateEndpoints(listOf(endpoint))
            assertTrue(apiIndex.isValid())
            
            apiIndex.invalidate()
            assertFalse(apiIndex.isValid())
            assertTrue(apiIndex.endpoints().isEmpty())
        }
    }

    fun testUpdateEndpoints() {
        runBlocking {
            val endpoints = listOf(
                ApiEndpoint(
                    name = "getUser",
                    metadata = HttpMetadata(
                        path = "/api/users/{id}",
                        method = HttpMethod.GET
                    ),
                    className = "com.example.UserController"
                ),
                ApiEndpoint(
                    name = "createUser",
                    metadata = HttpMetadata(
                        path = "/api/users",
                        method = HttpMethod.POST
                    ),
                    className = "com.example.UserController"
                )
            )
            
            apiIndex.updateEndpoints(endpoints)
            
            assertTrue(apiIndex.isValid())
            assertTrue(apiIndex.isReady())
            assertEquals(2, apiIndex.endpoints().size)
        }
    }

    fun testEndpointsByClass() {
        runBlocking {
            val endpoints = listOf(
                ApiEndpoint(
                    name = "getUser",
                    metadata = HttpMetadata(
                        path = "/api/users/{id}",
                        method = HttpMethod.GET
                    ),
                    className = "com.example.UserController"
                ),
                ApiEndpoint(
                    name = "getOrder",
                    metadata = HttpMetadata(
                        path = "/api/orders/{id}",
                        method = HttpMethod.GET
                    ),
                    className = "com.example.OrderController"
                )
            )
            
            apiIndex.updateEndpoints(endpoints)
            
            val userEndpoints = apiIndex.endpointsByClass("com.example.UserController")
            assertEquals(1, userEndpoints.size)
            assertEquals("getUser", userEndpoints.first().name)
            
            val orderEndpoints = apiIndex.endpointsByClass("com.example.OrderController")
            assertEquals(1, orderEndpoints.size)
            assertEquals("getOrder", orderEndpoints.first().name)
            
            val nonExistent = apiIndex.endpointsByClass("com.example.NonExistent")
            assertTrue(nonExistent.isEmpty())
        }
    }

    fun testUpdateEndpointsByClasses() {
        runBlocking {
            val classEndpoints = mapOf(
                "com.example.UserController" to listOf(
                    ApiEndpoint(
                        name = "getUser",
                        metadata = HttpMetadata(
                            path = "/api/users/{id}",
                            method = HttpMethod.GET
                        )
                    )
                ),
                "com.example.OrderController" to listOf(
                    ApiEndpoint(
                        name = "getOrder",
                        metadata = HttpMetadata(
                            path = "/api/orders/{id}",
                            method = HttpMethod.GET
                        )
                    )
                )
            )
            
            apiIndex.updateEndpointsByClasses(classEndpoints)
            
            assertTrue(apiIndex.isValid())
            assertTrue(apiIndex.isReady())
            assertEquals(2, apiIndex.endpoints().size)
        }
    }

    fun testUpdateEndpointsByClassesRemovesEmpty() {
        runBlocking {
            val initialEndpoints = mapOf(
                "com.example.UserController" to listOf(
                    ApiEndpoint(
                        name = "getUser",
                        metadata = HttpMetadata(
                            path = "/api/users/{id}",
                            method = HttpMethod.GET
                        )
                    )
                )
            )
            apiIndex.updateEndpointsByClasses(initialEndpoints)
            assertEquals(1, apiIndex.endpoints().size)
            
            val emptyEndpoints = mapOf(
                "com.example.UserController" to emptyList<ApiEndpoint>()
            )
            apiIndex.updateEndpointsByClasses(emptyEndpoints)
            
            assertTrue(apiIndex.endpoints().isEmpty())
        }
    }

    fun testRemoveEndpointsByClasses() {
        runBlocking {
            val endpoints = listOf(
                ApiEndpoint(
                    name = "getUser",
                    metadata = HttpMetadata(
                        path = "/api/users/{id}",
                        method = HttpMethod.GET
                    ),
                    className = "com.example.UserController"
                ),
                ApiEndpoint(
                    name = "getOrder",
                    metadata = HttpMetadata(
                        path = "/api/orders/{id}",
                        method = HttpMethod.GET
                    ),
                    className = "com.example.OrderController"
                )
            )
            
            apiIndex.updateEndpoints(endpoints)
            assertEquals(2, apiIndex.endpoints().size)
            
            apiIndex.removeEndpointsByClasses(setOf("com.example.UserController"))
            
            val remaining = apiIndex.endpoints()
            assertEquals(1, remaining.size)
            assertEquals("getOrder", remaining.first().name)
        }
    }

    fun testEndpointsAfterUpdate() {
        runBlocking {
            val endpoint = ApiEndpoint(
                name = "getUser",
                metadata = HttpMetadata(
                    path = "/api/users/{id}",
                    method = HttpMethod.GET
                ),
                className = "com.example.UserController"
            )
            apiIndex.updateEndpoints(listOf(endpoint))
            assertTrue(apiIndex.isReady())
            val endpoints = apiIndex.endpoints()
            assertEquals(1, endpoints.size)
        }
    }

    fun testEndpointsByClassAfterUpdate() {
        runBlocking {
            val endpoint = ApiEndpoint(
                name = "getUser",
                metadata = HttpMetadata(
                    path = "/api/users/{id}",
                    method = HttpMethod.GET
                ),
                className = "com.example.UserController"
            )
            apiIndex.updateEndpoints(listOf(endpoint))
            assertTrue(apiIndex.isReady())
            val endpoints = apiIndex.endpointsByClass("com.example.UserController")
            assertEquals(1, endpoints.size)
        }
    }
}
