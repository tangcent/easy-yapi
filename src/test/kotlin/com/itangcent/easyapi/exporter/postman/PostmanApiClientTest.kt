package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.exporter.model.*
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class PostmanApiClientTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testFormat(): Unit = runBlocking {
        val result = testFormatInternal()
        assertNotNull(result)
        assertEquals("Test Collection", result.info?.name)
    }

    private suspend fun testFormatInternal(): com.itangcent.easyapi.exporter.postman.model.PostmanCollection {
        val formatter = PostmanFormatter(
            project = project,
            options = PostmanFormatOptions(appendTimestamp = false)
        )
        val endpoints = listOf(createTestEndpoint())
        return formatter.format(endpoints, "Test Collection")
    }

    private fun createTestEndpoint(): ApiEndpoint {
        return ApiEndpoint(
            name = "Test API",
            description = "Test API description",
            metadata = httpMetadata(
                path = "/api/test",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(
                        name = "id",
                        required = true,
                        binding = ParameterBinding.Query,
                        example = "123"
                    )
                ),
                headers = emptyList(),
                contentType = "application/json"
            )
        )
    }
}
