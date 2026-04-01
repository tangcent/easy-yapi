package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.mockito.Mockito

class RequestPanelTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var httpClient: HttpClient
    private lateinit var requestPanel: RequestPanel

    override fun setUp() {
        super.setUp()
        httpClient = Mockito.mock(HttpClient::class.java)
        requestPanel = RequestPanel(httpClient)
    }

    fun testPanelCreation() {
        assertNotNull(requestPanel)
    }

    fun testSetEndpoint() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            path = "/api/users/1",
            method = HttpMethod.GET
        )
        
        requestPanel.setEndpoint(endpoint)
    }

    fun testSetEndpointWithPost() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            path = "/api/users",
            method = HttpMethod.POST,
            contentType = "application/json"
        )
        
        requestPanel.setEndpoint(endpoint)
    }

    fun testSetEndpointWithParameters() {
        val endpoint = ApiEndpoint(
            name = "List Users",
            path = "/api/users",
            method = HttpMethod.GET
        )
        
        requestPanel.setEndpoint(endpoint)
    }

    fun testDispose() {
        requestPanel.dispose()
    }
}
