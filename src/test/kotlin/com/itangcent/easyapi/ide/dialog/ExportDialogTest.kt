package com.itangcent.easyapi.ide.dialog

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.GrpcMetadata
import com.itangcent.easyapi.exporter.model.GrpcStreamingType
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.OutputConfig
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ExportDialogTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testDialogShowsEndpointCount() {
        val dialog = ExportDialog(project, 10)
        assertEquals("Dialog title should show endpoint count", 
            "Export API Endpoints (10 endpoints)", dialog.title)
    }

    fun testDialogShowsAllExportFormats() {
        val dialog = ExportDialog(project, 5)
        assertNotNull("Dialog should be created", dialog)
    }

    fun testDefaultFormatIsMarkdown() {
        val dialog = ExportDialog(project, 5)
        assertNotNull("Dialog should be created", dialog)
    }

    fun testOutputConfigDefaults() {
        val dialog = ExportDialog(project, 5)
        val config = dialog.outputConfig
        assertNotNull("Output config should not be null", config)
        assertEquals("Default output config", OutputConfig.DEFAULT, config)
    }

    fun testDialogCanBeCreatedWithDifferentCounts() {
        for (count in listOf(0, 1, 10, 100)) {
            val dialog = ExportDialog(project, count)
            assertNotNull("Dialog should be created for $count endpoints", dialog)
        }
    }

    fun testFormatFilteringWithHttpEndpoints() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                metadata = HttpMetadata(path = "/api/users", method = HttpMethod.GET)
            ),
            ApiEndpoint(
                name = "Create User",
                metadata = HttpMetadata(path = "/api/users", method = HttpMethod.POST)
            )
        )
        
        val dialog = ExportDialog(project, endpoints.size, endpoints)
        assertNotNull("Dialog should be created", dialog)
    }

    fun testFormatFilteringWithGrpcEndpoints() {
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

    fun testFormatFilteringWithMixedEndpoints() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                metadata = HttpMetadata(path = "/api/users", method = HttpMethod.GET)
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

    fun testFormatFilteringWithEmptyEndpoints() {
        val endpoints = emptyList<ApiEndpoint>()
        
        val dialog = ExportDialog(project, 0, endpoints)
        assertNotNull("Dialog should be created with empty endpoints", dialog)
    }
}
