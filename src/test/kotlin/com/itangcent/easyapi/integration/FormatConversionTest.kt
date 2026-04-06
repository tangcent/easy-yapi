package com.itangcent.easyapi.integration

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.model.ParameterType
import com.itangcent.easyapi.exporter.postman.PostmanFormatOptions
import com.itangcent.easyapi.exporter.postman.PostmanFormatter
import com.itangcent.easyapi.exporter.curl.CurlFormatter
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class FormatConversionTest {

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

    @Test
    fun testPostmanFormatConversion() = runBlocking {
        val context = ActionContext.builder()
            .bind(ConfigReader::class, TestConfigReader.EMPTY)
            .dispatcher(Dispatchers.Unconfined)
            .withSpiBindings().build()
        withContext(context.coroutineContext) {
            val postmanFormatter = PostmanFormatter(
                actionContext = context,
                options = PostmanFormatOptions(buildExample = true, autoMergeScript = true)
            )
            val endpoints = listOf(testEndpoint, testPostEndpoint)
            val collection = postmanFormatter.format(endpoints, "Test API")

            assertNotNull("Collection should not be null", collection)
            assertTrue("Collection name should start with Test API", collection.info?.name?.startsWith("Test API") == true)
            assertTrue("Should have items", collection.item?.isNotEmpty() == true)
        }
    }

    @Test
    fun testCurlFormatConversion() {
        val curl = CurlFormatter.format(testPostEndpoint, "https://api.example.com")

        assertNotNull("cURL output should not be null", curl)
        assertTrue("Should contain POST method", curl.contains("POST"))
        assertTrue("Should contain URL", curl.contains("https://api.example.com/api/users"))
        assertTrue("Should contain Content-Type", curl.contains("application/json"))
    }

    @Test
    fun testJson5FormatConversion() {
        // Json5Formatter removed — JSON5 formatting now handled by YapiFormatter.formatAsJson5 on ObjectModel
    }

    @Test
    fun testMultipleEndpointsFormat() = runBlocking {
        val context = ActionContext.builder()
            .bind(ConfigReader::class, TestConfigReader.EMPTY)
            .dispatcher(Dispatchers.Unconfined)
            .withSpiBindings().build()
        withContext(context.coroutineContext) {
            val postmanFormatter = PostmanFormatter(
                actionContext = context,
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
}
