package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.model.ParameterType
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class PostmanApiClientTest {

    @Test
    fun testFormat(): Unit = runBlocking {
        val result = testFormatInternal()
        assertNotNull(result)
        assertEquals("Test Collection", result.info?.name)
    }

    private suspend fun testFormatInternal(): com.itangcent.easyapi.exporter.postman.model.PostmanCollection {
        val context = ActionContext.builder()
            .bind(ConfigReader::class, TestConfigReader.EMPTY)
            .withSpiBindings()
            .dispatcher(Dispatchers.Unconfined)
            .build()
        return withContext(context.coroutineContext) {
            val formatter = PostmanFormatter(
                actionContext = context,
                options = PostmanFormatOptions(appendTimestamp = false)
            )
            val endpoints = listOf(createTestEndpoint())
            formatter.format(endpoints, "Test Collection")
        }
    }

    private fun createTestEndpoint(): ApiEndpoint {
        return ApiEndpoint(
            name = "Test API",
            description = "Test API description",
            metadata = HttpMetadata(
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
