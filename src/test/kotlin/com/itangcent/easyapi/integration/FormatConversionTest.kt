package com.itangcent.easyapi.integration

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.postman.PostmanFormatOptions
import com.itangcent.easyapi.exporter.postman.PostmanFormatter
import com.itangcent.easyapi.exporter.curl.CurlFormatter
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class FormatConversionTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.EMPTY

    private val testEndpoint = ApiEndpoint(
        name = "Get User",
        description = "Retrieve user by ID",
        metadata = HttpMetadata(
            path = "/api/users/{id}",
            method = HttpMethod.GET,
            parameters = listOf(
                ApiParameter(name = "id", binding = ParameterBinding.Path, example = "1")
            )
        )
    )

    private val testPostEndpoint = ApiEndpoint(
        name = "Create User",
        description = "Create a new user",
        metadata = HttpMetadata(
            path = "/api/users",
            method = HttpMethod.POST,
            contentType = "application/json",
            parameters = listOf(
                ApiParameter(name = "name", binding = ParameterBinding.Body, example = "John"),
                ApiParameter(name = "email", binding = ParameterBinding.Body, example = "john@example.com")
            )
        )
    )

    fun testPostmanFormatConversion(): Unit = runBlocking {
        val postmanFormatter = PostmanFormatter(
            project = project,
            options = PostmanFormatOptions(buildExample = true, autoMergeScript = true)
        )
        val endpoints = listOf(testEndpoint, testPostEndpoint)
        val collection = postmanFormatter.format(endpoints, "Test API")

        assertNotNull("Collection should not be null", collection)
        assertTrue("Collection name should start with Test API", collection.info?.name?.startsWith("Test API") == true)
        assertTrue("Should have items", collection.item?.isNotEmpty() == true)
    }

    fun testCurlFormatConversion() {
        val curl = CurlFormatter.format(testPostEndpoint, "https://api.example.com")

        assertNotNull("cURL output should not be null", curl)
        assertTrue("Should contain POST method", curl.contains("POST"))
        assertTrue("Should contain URL", curl.contains("https://api.example.com/api/users"))
        assertTrue("Should contain Content-Type", curl.contains("application/json"))
    }

    fun testJson5FormatConversion() {
        // Json5Formatter removed — JSON5 formatting now handled by YapiFormatter.formatAsJson5 on ObjectModel
    }

    fun testMultipleEndpointsFormat(): Unit = runBlocking {
        val postmanFormatter = PostmanFormatter(
            project = project,
            options = PostmanFormatOptions(buildExample = true, autoMergeScript = true)
        )
        val endpoints = listOf(
            testEndpoint,
            testPostEndpoint,
            ApiEndpoint(
                name = "Update User",
                metadata = HttpMetadata(
                    path = "/api/users/{id}",
                    method = HttpMethod.PUT,
                    contentType = "application/json",
                    parameters = listOf(
                        ApiParameter(name = "id", binding = ParameterBinding.Path, example = "1"),
                        ApiParameter(name = "name", binding = ParameterBinding.Body, example = "Updated")
                    )
                )
            ),
            ApiEndpoint(
                name = "Delete User",
                metadata = HttpMetadata(
                    path = "/api/users/{id}",
                    method = HttpMethod.DELETE,
                    parameters = listOf(
                        ApiParameter(name = "id", binding = ParameterBinding.Path, example = "1")
                    )
                )
            )
        )

        val collection = postmanFormatter.format(endpoints, "CRUD API")
        assertTrue("Should have items", collection.item?.isNotEmpty() == true)
    }
}
