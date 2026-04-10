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
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.postman.model.*
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PostmanFormatterTest {

    @Test
    fun testParsePath() {
        assertEquals(listOf("api", "users"), PostmanFormatter.parsePath("/api/users"))
        assertEquals(listOf("api", "users", ":id"), PostmanFormatter.parsePath("/api/users/{id}"))
        assertEquals(listOf("api", "users", ":id"), PostmanFormatter.parsePath("/api/users/{id:\\d+}"))
    }

    @Test
    fun testBuildUrlSimple() {
        val endpoint = ApiEndpoint(
            name = "Get Users",
            metadata = HttpMetadata(
                path = "/api/users",
                method = HttpMethod.GET
            )
        )

        val formatter = TestablePostmanFormatter()
        val url = formatter.testBuildUrl(endpoint, "{{host}}")

        assertEquals("{{host}}/api/users", url.raw)
        assertTrue(url.query.isNullOrEmpty())
    }

    @Test
    fun testBuildUrlWithQuery() {
        val endpoint = ApiEndpoint(
            name = "List Users",
            metadata = HttpMetadata(
                path = "/api/users",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(name = "page", binding = ParameterBinding.Query, example = "1"),
                    ApiParameter(name = "size", binding = ParameterBinding.Query, example = "10")
                )
            )
        )

        val formatter = TestablePostmanFormatter()
        val url = formatter.testBuildUrl(endpoint, "{{host}}")

        assertTrue(url.raw.contains("page=1"))
        assertTrue(url.raw.contains("size=10"))
        assertEquals(2, url.query?.size)
    }

    @Test
    fun testBuildUrlWithPathVariable() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = HttpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(name = "id", binding = ParameterBinding.Path, example = "123")
                )
            )
        )

        val formatter = TestablePostmanFormatter()
        val url = formatter.testBuildUrl(endpoint, "{{host}}")

        assertEquals(1, url.variable?.size)
        assertEquals("id", url.variable?.get(0)?.key)
        assertEquals("123", url.variable?.get(0)?.value)
    }

    @Test
    fun testBuildBodyJson() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = HttpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                contentType = "application/json",
                parameters = listOf(
                    ApiParameter(name = "name", binding = ParameterBinding.Body, example = "John"),
                    ApiParameter(name = "age", binding = ParameterBinding.Body, example = "25")
                )
            )
        )

        val formatter = TestablePostmanFormatter()
        val body = formatter.testBuildBody(endpoint)

        assertNotNull(body)
        assertEquals("raw", body?.mode)
        assertTrue(body?.raw?.contains("name") == true)
        assertTrue(body?.raw?.contains("John") == true)
    }

    @Test
    fun testBuildBodyFormUrlencoded() {
        val endpoint = ApiEndpoint(
            name = "Login",
            metadata = HttpMetadata(
                path = "/api/login",
                method = HttpMethod.POST,
                contentType = "application/x-www-form-urlencoded",
                parameters = listOf(
                    ApiParameter(name = "username", binding = ParameterBinding.Form, example = "admin"),
                    ApiParameter(name = "password", binding = ParameterBinding.Form, example = "secret")
                )
            )
        )

        val formatter = TestablePostmanFormatter()
        val body = formatter.testBuildBody(endpoint)

        assertNotNull(body)
        assertEquals("urlencoded", body?.mode)
        assertEquals(2, body?.urlencoded?.size)
        assertEquals("username", body?.urlencoded?.get(0)?.key)
        assertEquals("admin", body?.urlencoded?.get(0)?.value)
    }

    @Test
    fun testBuildBodyMultipart() {
        val endpoint = ApiEndpoint(
            name = "Upload",
            metadata = HttpMetadata(
                path = "/api/upload",
                method = HttpMethod.POST,
                contentType = "multipart/form-data",
                parameters = listOf(
                    ApiParameter(name = "file", binding = ParameterBinding.Form, example = "@/path/to/file"),
                    ApiParameter(name = "description", binding = ParameterBinding.Form, example = "My file")
                )
            )
        )

        val formatter = TestablePostmanFormatter()
        val body = formatter.testBuildBody(endpoint)

        assertNotNull(body)
        assertEquals("formdata", body?.mode)
        assertEquals(2, body?.formdata?.size)
    }

    @Test
    fun testBuildBodyWithEndpointBody() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = HttpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                contentType = "application/json",
                body = ObjectModel.Object(
                    mapOf(
                        "name" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                        "age" to FieldModel(ObjectModel.Single(JsonType.INT)),
                        "address" to FieldModel(
                            ObjectModel.Object(
                                mapOf(
                                    "city" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                                    "country" to FieldModel(ObjectModel.Single(JsonType.STRING))
                                )
                            )
                        )
                    )
                )
            )
        )

        val formatter = TestablePostmanFormatter()
        val body = formatter.testBuildBody(endpoint)

        assertNotNull(body)
        assertEquals("raw", body?.mode)
        assertTrue(body?.raw?.contains("address") == true)
        assertTrue(body?.raw?.contains("city") == true)
    }

    @Test
    fun testToItemWithFormFileType(): Unit = runBlocking {
        val context = ActionContext.builder()
            .bind(ConfigReader::class, TestConfigReader.EMPTY)
            .withSpiBindings().build()
        val formatter = PostmanFormatter(actionContext = context)
        val endpoint = ApiEndpoint(
            name = "Upload File",
            metadata = HttpMetadata(
                path = "/api/upload",
                method = HttpMethod.POST,
                contentType = "multipart/form-data",
                parameters = listOf(
                    ApiParameter(name = "file", type = ParameterType.FILE, binding = ParameterBinding.Form, example = "/path/to/file.txt"),
                    ApiParameter(name = "description", binding = ParameterBinding.Form, example = "Test file")
                )
            )
        )
        val item = formatter.toItem(endpoint)
        assertNotNull(item)
        assertEquals("Upload File", item.name)
        assertNotNull(item.request?.body)
        assertEquals("formdata", item.request?.body?.mode)
        assertNotNull(item.request?.body?.formdata)
        val formdata = item.request?.body?.formdata
        assertEquals(2, formdata?.size ?: 0)
        val fileParam = formdata?.firstOrNull { it.key == "file" }
        assertNotNull(fileParam)
        assertEquals("file", fileParam?.type)
    }

    @Test
    fun testToItemWithUrlencodedFileType(): Unit = runBlocking {
        val context = ActionContext.builder()
            .bind(ConfigReader::class, TestConfigReader.EMPTY)
            .withSpiBindings().build()
        val formatter = PostmanFormatter(actionContext = context)
        val endpoint = ApiEndpoint(
            name = "Submit Form",
            metadata = HttpMetadata(
                path = "/api/submit",
                method = HttpMethod.POST,
                contentType = "application/x-www-form-urlencoded",
                parameters = listOf(
                    ApiParameter(name = "profileImg", type = ParameterType.FILE, binding = ParameterBinding.Form, example = "/path/to/image.png"),
                    ApiParameter(name = "name", binding = ParameterBinding.Form, example = "John")
                )
            )
        )
        val item = formatter.toItem(endpoint)
        assertNotNull(item)
        assertNotNull(item.request?.body)
        assertEquals("urlencoded", item.request?.body?.mode)
        assertNotNull(item.request?.body?.urlencoded)
        val urlencoded = item.request?.body?.urlencoded
        assertEquals(2, urlencoded?.size ?: 0)
        val fileParam = urlencoded?.firstOrNull { it.key == "profileImg" }
        assertNotNull(fileParam)
        assertEquals("file", fileParam?.type)
    }

    @Test
    fun testFormatWithJsonBody(): Unit = runBlocking {
        val context = ActionContext.builder()
            .bind(ConfigReader::class, TestConfigReader.EMPTY)
            .withSpiBindings().build()
        val formatter = PostmanFormatter(
            actionContext = context,
            options = PostmanFormatOptions(appendTimestamp = false)
        )
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = HttpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                contentType = "application/json",
                body = ObjectModel.Object(
                    mapOf(
                        "name" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                        "age" to FieldModel(ObjectModel.Single(JsonType.INT))
                    )
                )
            )
        )
        val collection = formatter.format(listOf(endpoint), "Test API")
        assertNotNull(collection)
        assertEquals("Test API", collection.info?.name)
        assertTrue(collection.item?.isNotEmpty() == true)
        val item = collection.item?.firstOrNull()
        assertNotNull(item?.request?.body)
        assertEquals("raw", item?.request?.body?.mode)
    }

    @Test
    fun testFormatMultipleEndpoints(): Unit = runBlocking {
        val context = ActionContext.builder()
            .bind(ConfigReader::class, TestConfigReader.EMPTY)
            .withSpiBindings().build()
        val formatter = PostmanFormatter(
            actionContext = context,
            options = PostmanFormatOptions(appendTimestamp = false)
        )
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                metadata = HttpMetadata(
                    path = "/api/users/{id}",
                    method = HttpMethod.GET,
                    parameters = listOf(
                        ApiParameter(name = "id", binding = ParameterBinding.Path, example = "1")
                    )
                )
            ),
            ApiEndpoint(
                name = "Create User",
                metadata = HttpMetadata(
                    path = "/api/users",
                    method = HttpMethod.POST,
                    contentType = "application/json",
                    parameters = listOf(
                        ApiParameter(name = "name", binding = ParameterBinding.Body, example = "John")
                    )
                )
            )
        )
        val collection = formatter.format(endpoints, "Test API")
        assertNotNull(collection)
        assertEquals("Test API", collection.info?.name)
        assertTrue(collection.item?.isNotEmpty() == true)
        assertEquals(2, collection.item?.size ?: 0)
    }

    @Test
    fun testFormatFiltersOutGrpcEndpoints(): Unit = runBlocking {
        val context = ActionContext.builder()
            .bind(ConfigReader::class, TestConfigReader.EMPTY)
            .withSpiBindings().build()
        val formatter = PostmanFormatter(
            actionContext = context,
            options = PostmanFormatOptions(appendTimestamp = false)
        )
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                metadata = HttpMetadata(
                    path = "/api/users/{id}",
                    method = HttpMethod.GET
                )
            ),
            ApiEndpoint(
                name = "GetUser",
                metadata = com.itangcent.easyapi.exporter.model.GrpcMetadata(
                    path = "/com.example.UserService/GetUser",
                    serviceName = "UserService",
                    methodName = "GetUser",
                    packageName = "com.example",
                    streamingType = com.itangcent.easyapi.exporter.model.GrpcStreamingType.UNARY
                )
            ),
            ApiEndpoint(
                name = "Create User",
                metadata = HttpMetadata(
                    path = "/api/users",
                    method = HttpMethod.POST
                )
            )
        )
        val collection = formatter.format(endpoints, "Test API")
        assertNotNull(collection)
        assertEquals("Test API", collection.info?.name)
        // gRPC endpoint should be filtered out, only HTTP endpoints remain
        assertEquals(2, collection.item?.size ?: 0)
        val itemNames = collection.item?.map { it.name } ?: emptyList()
        assertTrue(itemNames.contains("Get User"))
        assertTrue(itemNames.contains("Create User"))
        assertFalse(itemNames.contains("GetUser"))
    }
}

class TestablePostmanFormatter {
    fun testBuildUrl(endpoint: ApiEndpoint, hostVar: String): PostmanUrl {
        val httpMeta = endpoint.httpMetadata
        val parameters = httpMeta?.parameters ?: emptyList()

        val query = parameters
            .filter { it.binding == ParameterBinding.Query }
            .map {
                PostmanQuery(
                    key = it.name,
                    value = it.example ?: it.defaultValue ?: "",
                    equals = true,
                    description = it.description
                )
            }

        val pathSegments = PostmanFormatter.parsePath(httpMeta?.path ?: "")

        val pathVariables = parameters
            .filter { it.binding == ParameterBinding.Path }
            .map {
                PostmanPathVariable(
                    key = it.name,
                    value = it.example ?: it.defaultValue ?: "",
                    description = it.description
                )
            }

        val queryString = if (query.isEmpty()) "" else "?" + query.joinToString("&") { "${it.key}=${it.value}" }
        val raw = hostVar.trimEnd('/') + "/" + pathSegments.joinToString("/") + queryString

        return PostmanUrl(
            raw = raw,
            host = listOf(hostVar),
            path = pathSegments,
            query = query,
            variable = pathVariables
        )
    }

    fun testBuildBody(endpoint: ApiEndpoint): PostmanBody? {
        val httpMeta = endpoint.httpMetadata
        val contentType = httpMeta?.contentType?.lowercase().orEmpty()
        val parameters = httpMeta?.parameters ?: emptyList()
        val bodyParams = parameters.filter { it.binding == ParameterBinding.Body }
        val formParams = parameters.filter { it.binding == ParameterBinding.Form }

        if (contentType.contains("json")) {
            val raw = when {
                httpMeta?.body != null -> com.itangcent.easyapi.util.GsonUtils.prettyJson(httpMeta.body)
                bodyParams.isNotEmpty() -> bodyParams.associate { it.name to (it.example ?: it.defaultValue ?: "") }
                    .let { com.itangcent.easyapi.util.GsonUtils.prettyJson(it) }
                else -> "{}"
            }
            return PostmanBody(
                mode = "raw",
                raw = raw,
                options = mapOf("raw" to mapOf("language" to "json"))
            )
        }

        if (contentType.contains("x-www-form-urlencoded")) {
            return PostmanBody(
                mode = "urlencoded",
                urlencoded = formParams.map {
                    PostmanFormParam(
                        key = it.name,
                        value = it.example ?: it.defaultValue ?: "",
                        type = it.type.rawType(),
                        description = it.description
                    )
                }
            )
        }

        if (contentType.contains("multipart") || contentType.contains("form-data")) {
            return PostmanBody(
                mode = "formdata",
                formdata = formParams.map {
                    PostmanFormParam(
                        key = it.name,
                        value = it.example ?: it.defaultValue ?: "",
                        type = it.type.rawType(),
                        description = it.description
                    )
                }
            )
        }

        if (httpMeta?.body != null) {
            return PostmanBody(
                mode = "raw",
                raw = com.itangcent.easyapi.util.GsonUtils.prettyJson(httpMeta.body),
                options = mapOf("raw" to mapOf("language" to "json"))
            )
        }

        return null
    }
}
