package com.itangcent.easyapi.testFramework

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.model.ParameterType
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.settings.Settings

object ApiFixtures {

    fun createEndpoint(
        name: String = "testEndpoint",
        path: String = "/api/test",
        method: HttpMethod = HttpMethod.GET,
        description: String? = "Test endpoint",
        folder: String? = null
    ): ApiEndpoint {
        return ApiEndpoint(
            name = name,
            description = description,
            folder = folder,
            metadata = httpMetadata(
                path = path,
                method = method,
                parameters = emptyList(),
                headers = emptyList()
            )
        )
    }

    fun createGetEndpoint(
        name: String = "getUser",
        path: String = "/api/users/{id}"
    ): ApiEndpoint {
        return ApiEndpoint(
            name = name,
            description = "Get user by ID",
            folder = "User API",
            metadata = httpMetadata(
                path = path,
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(
                        name = "id",
                        binding = ParameterBinding.Path,
                        description = "User ID",
                        required = true
                    )
                ),
                headers = listOf(
                    ApiHeader("Authorization", "Bearer token")
                )
            )
        )
    }

    fun createPostEndpoint(
        name: String = "createUser",
        path: String = "/api/users"
    ): ApiEndpoint {
        return ApiEndpoint(
            name = name,
            description = "Create a new user",
            folder = "User API",
            metadata = httpMetadata(
                path = path,
                method = HttpMethod.POST,
                parameters = listOf(
                    ApiParameter(
                        name = "name",
                        binding = ParameterBinding.Body,
                        description = "User name",
                        required = true,
                        example = "John Doe"
                    ),
                    ApiParameter(
                        name = "email",
                        binding = ParameterBinding.Body,
                        description = "User email",
                        required = true,
                        example = "john@example.com"
                    )
                ),
                headers = listOf(
                    ApiHeader("Content-Type", "application/json"),
                    ApiHeader("Authorization", "Bearer token")
                )
            )
        )
    }

    fun createSampleEndpoints(): List<ApiEndpoint> {
        return listOf(
            createGetEndpoint("getUser", "/api/users/{id}"),
            createGetEndpoint("listUsers", "/api/users"),
            createPostEndpoint("createUser", "/api/users"),
            createEndpoint(
                name = "updateUser",
                path = "/api/users/{id}",
                method = HttpMethod.PUT,
                folder = "User API"
            ),
            createEndpoint(
                name = "deleteUser",
                path = "/api/users/{id}",
                method = HttpMethod.DELETE,
                folder = "User API"
            )
        )
    }

    fun createFileUploadEndpoint(
        name: String = "uploadFile",
        path: String = "/api/upload"
    ): ApiEndpoint {
        return ApiEndpoint(
            name = name,
            description = "Upload a file",
            folder = "File API",
            metadata = httpMetadata(
                path = path,
                method = HttpMethod.POST,
                parameters = listOf(
                    ApiParameter(
                        name = "file",
                        type = ParameterType.FILE,
                        binding = ParameterBinding.Form,
                        description = "File to upload",
                        required = true
                    )
                ),
                headers = listOf(
                    ApiHeader("Content-Type", "multipart/form-data")
                )
            )
        )
    }

    fun createSettings(): Settings {
        return Settings().apply {
            yapiServer = "http://localhost:3000"
            yapiTokens = "test-project:abc123"
            postmanToken = "test-token"
            outputCharset = "UTF-8"
            httpTimeOut = 30000
            unsafeSsl = false
            feignEnable = true
            jaxrsEnable = false
        }
    }
}
