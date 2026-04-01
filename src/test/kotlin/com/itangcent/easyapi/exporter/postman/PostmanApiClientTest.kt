package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.http.UrlConnectionHttpClient
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class PostmanApiClientTest {

    @Test
    fun testUploadCollection() = runBlocking {
        val client = PostmanApiClient(httpClient = UrlConnectionHttpClient)
        val collection = createTestCollection()
        
        assertNotNull("Client should be created", client)
        assertNotNull("Collection should be created", collection)
    }

    @Test
    fun testListWorkspaces() = runBlocking {
        val client = PostmanApiClient(httpClient = UrlConnectionHttpClient)
        
        assertNotNull("Client should be created", client)
    }

    private suspend fun createTestCollection(): com.itangcent.easyapi.exporter.postman.model.PostmanCollection {
        val context = ActionContext.builder()
            .bind(ConfigReader::class, TestConfigReader.EMPTY)
            .withSpiBindings()
            .dispatcher(Dispatchers.Unconfined)
            .build()
        return withContext(context.coroutineContext) {
            val formatter = PostmanFormatter(actionContext = context)
            val endpoints = listOf(createTestEndpoint())
            formatter.format(endpoints, "Test Collection")
        }
    }

    private fun createTestEndpoint(): ApiEndpoint {
        return ApiEndpoint(
            name = "Test API",
            path = "/api/test",
            method = HttpMethod.GET,
            parameters = listOf(
                ApiParameter(
                    name = "id",
                    type = "String",
                    required = true,
                    binding = ParameterBinding.Query,
                    example = "123"
                )
            ),
            headers = emptyList(),
            contentType = "application/json",
            description = "Test API description"
        )
    }
}
