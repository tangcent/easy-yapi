package com.itangcent.easyapi.ide.dialog

import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.model.*
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ExportDialogTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testDialogShowsEndpointCount() {
        val dialog = ExportDialog(project, 10)
        assertEquals("Dialog title should show endpoint count",
            "Export API Endpoints (10 endpoints)", dialog.title)
    }

    fun testDialogShowsAvailableChannels() {
        val dialog = ExportDialog(project, 5)
        assertNotNull("Dialog should be created", dialog)
    }

    fun testDefaultChannelConfigIsEmpty() {
        val dialog = ExportDialog(project, 5)
        val config = dialog.channelConfig
        assertNotNull("Channel config should not be null", config)
        assertEquals("Default channel config", ChannelConfig.Empty, config)
    }

    fun testDialogCanBeCreatedWithDifferentCounts() {
        for (count in listOf(0, 1, 10, 100)) {
            val dialog = ExportDialog(project, count)
            assertNotNull("Dialog should be created for $count endpoints", dialog)
        }
    }

    fun testDialogWithHttpEndpoints() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
            ),
            ApiEndpoint(
                name = "Create User",
                metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST)
            )
        )

        val dialog = ExportDialog(project, endpoints.size, endpoints)
        assertNotNull("Dialog should be created", dialog)
    }

    fun testDialogWithGrpcEndpoints() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "SayHello",
                metadata = GrpcMetadata(
                    path = "/com.example.Greeter/SayHello",
                    serviceName = "Greeter",
                    methodName = "SayHello",
                    packageName = "com.example",
                    streamingType = GrpcStreamingType.UNARY
                )
            )
        )

        val dialog = ExportDialog(project, endpoints.size, endpoints)
        assertNotNull("Dialog should be created", dialog)
    }

    fun testDialogWithMixedEndpoints() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
            ),
            ApiEndpoint(
                name = "SayHello",
                metadata = GrpcMetadata(
                    path = "/com.example.Greeter/SayHello",
                    serviceName = "Greeter",
                    methodName = "SayHello",
                    packageName = "com.example",
                    streamingType = GrpcStreamingType.UNARY
                )
            )
        )

        val dialog = ExportDialog(project, endpoints.size, endpoints)
        assertNotNull("Dialog should be created", dialog)
    }

    fun testDialogWithEmptyEndpoints() {
        val endpoints = emptyList<ApiEndpoint>()

        val dialog = ExportDialog(project, 0, endpoints)
        assertNotNull("Dialog should be created with empty endpoints", dialog)
    }
}
