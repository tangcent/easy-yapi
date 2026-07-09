package com.itangcent.easyapi.exporter.channel.curl

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.GrpcMetadata
import com.itangcent.easyapi.exporter.model.GrpcStreamingType
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import org.junit.Assert.*
import org.junit.Test

class CurlFormatterTest {

    @Test
    fun testFormatSimpleGet() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/1",
                method = HttpMethod.GET
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("curl"))
        assertTrue(result.contains("-X GET"))
        assertTrue(result.contains("http://localhost:8080/api/users/1"))
    }

    @Test
    fun testFormatPostWithJsonBody() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                contentType = "application/json",
                parameters = listOf(
                    ApiParameter(
                        name = "name",
                        binding = ParameterBinding.Body,
                        example = "John"
                    ),
                    ApiParameter(
                        name = "age",
                        binding = ParameterBinding.Body,
                        example = "25"
                    )
                )
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("-X POST"))
        assertTrue(result.contains("Content-Type"))
        assertTrue(result.contains("-d"))
        assertTrue(result.contains("name"))
        assertTrue(result.contains("John"))
    }

    @Test
    fun testFormatWithQueryParams() {
        val endpoint = ApiEndpoint(
            name = "List Users",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(
                        name = "page",
                        binding = ParameterBinding.Query,
                        example = "1"
                    ),
                    ApiParameter(
                        name = "size",
                        binding = ParameterBinding.Query,
                        example = "10"
                    )
                )
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("page=1"))
        assertTrue(result.contains("size=10"))
    }

    @Test
    fun testFormatWithHeaders() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/1",
                method = HttpMethod.GET,
                headers = listOf(
                    ApiHeader("Authorization", "Bearer token123"),
                    ApiHeader("X-Request-Id", "abc123")
                )
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("-H 'Authorization: Bearer token123'"))
        assertTrue(result.contains("-H 'X-Request-Id: abc123'"))
    }

    @Test
    fun testFormatWithFormUrlencoded() {
        val endpoint = ApiEndpoint(
            name = "Login",
            metadata = httpMetadata(
                path = "/api/login",
                method = HttpMethod.POST,
                contentType = "application/x-www-form-urlencoded",
                parameters = listOf(
                    ApiParameter(
                        name = "username",
                        binding = ParameterBinding.Form,
                        example = "admin"
                    ),
                    ApiParameter(
                        name = "password",
                        binding = ParameterBinding.Form,
                        example = "secret"
                    )
                )
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("-X POST"))
        assertTrue(result.contains("--data-urlencode"))
        assertTrue(result.contains("username=admin"))
        assertTrue(result.contains("password=secret"))
    }

    @Test
    fun testFormatWithMultipartFormData() {
        val endpoint = ApiEndpoint(
            name = "Upload File",
            metadata = httpMetadata(
                path = "/api/upload",
                method = HttpMethod.POST,
                contentType = "multipart/form-data",
                parameters = listOf(
                    ApiParameter(
                        name = "file",
                        binding = ParameterBinding.Form,
                        example = "@/path/to/file"
                    ),
                    ApiParameter(
                        name = "description",
                        binding = ParameterBinding.Form,
                        example = "My file"
                    )
                )
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("-F"))
        assertTrue(result.contains("file=@/path/to/file"))
        assertTrue(result.contains("description=My file"))
    }

    @Test
    fun testEscapeShell() {
        val input = "it's a test"
        val result = CurlFormatter.escapeShell(input)
        assertEquals("it'\\''s a test", result)
    }

    @Test
    fun testFormatWithSpecialCharsInPath() {
        val endpoint = ApiEndpoint(
            name = "Search",
            metadata = httpMetadata(
                path = "/api/search/test's query",
                method = HttpMethod.GET
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("test'\\''s"))
    }

    @Test
    fun testFormatWithPathVariable() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(
                        name = "id",
                        binding = ParameterBinding.Path,
                        example = "123"
                    )
                )
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("/api/users/{id}"))
    }

    @Test
    fun testFormatPutRequest() {
        val endpoint = ApiEndpoint(
            name = "Update User",
            metadata = httpMetadata(
                path = "/api/users/1",
                method = HttpMethod.PUT,
                contentType = "application/json",
                parameters = listOf(
                    ApiParameter(
                        name = "name",
                        binding = ParameterBinding.Body,
                        example = "Updated Name"
                    )
                )
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("-X PUT"))
        assertTrue(result.contains("Updated Name"))
    }

    @Test
    fun testFormatDeleteRequest() {
        val endpoint = ApiEndpoint(
            name = "Delete User",
            metadata = httpMetadata(
                path = "/api/users/1",
                method = HttpMethod.DELETE
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("-X DELETE"))
    }

    @Test
    fun testFormatGrpcBasic() {
        val endpoint = ApiEndpoint(
            name = "SayHello",
            metadata = GrpcMetadata(
                path = "/com.example.GreeterService/SayHello",
                serviceName = "GreeterService",
                methodName = "SayHello",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY
            )
        )

        val result = CurlFormatter.format(endpoint, "localhost:50051")

        assertTrue(result.startsWith("grpcurl"))
        assertTrue(result.contains("-plaintext"))
        assertTrue(result.contains("localhost:50051"))
        assertTrue(result.contains("com.example.GreeterService/SayHello"))
        assertFalse(result.contains("curl -X"))
    }

    @Test
    fun testFormatGrpcWithBody() {
        val body = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.single("string"))
            )
        )
        val endpoint = ApiEndpoint(
            name = "SayHello",
            metadata = GrpcMetadata(
                path = "/com.example.GreeterService/SayHello",
                serviceName = "GreeterService",
                methodName = "SayHello",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY,
                body = body
            )
        )

        val result = CurlFormatter.format(endpoint, "localhost:50051")

        assertTrue(result.contains("-d"))
        assertTrue(result.contains("name"))
    }

    @Test
    fun testFormatGrpcStripsHttpPrefix() {
        val endpoint = ApiEndpoint(
            name = "SayHello",
            metadata = GrpcMetadata(
                path = "/com.example.GreeterService/SayHello",
                serviceName = "GreeterService",
                methodName = "SayHello",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY
            )
        )

        val resultHttp = CurlFormatter.format(endpoint, "http://myhost:50051")
        assertTrue(resultHttp.contains("myhost:50051"))
        assertFalse(resultHttp.contains("http://"))

        val resultHttps = CurlFormatter.format(endpoint, "https://myhost:50051")
        assertTrue(resultHttps.contains("myhost:50051"))
        assertFalse(resultHttps.contains("https://"))
    }

    @Test
    fun testFormatGrpcDefaultHost() {
        val endpoint = ApiEndpoint(
            name = "SayHello",
            metadata = GrpcMetadata(
                path = "/com.example.GreeterService/SayHello",
                serviceName = "GreeterService",
                methodName = "SayHello",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY
            )
        )

        val result = CurlFormatter.format(endpoint)

        assertTrue(result.contains("localhost:50051"))
    }

    @Test
    fun testFormatGrpcNoBody() {
        val endpoint = ApiEndpoint(
            name = "SayHello",
            metadata = GrpcMetadata(
                path = "/com.example.GreeterService/SayHello",
                serviceName = "GreeterService",
                methodName = "SayHello",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY
            )
        )

        val result = CurlFormatter.format(endpoint, "localhost:50051")

        assertFalse(result.contains("-d"))
    }

    @Test
    fun testFormatAllWithMixedEndpoints() {
        val httpEndpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/1",
                method = HttpMethod.GET
            )
        )
        val grpcEndpoint = ApiEndpoint(
            name = "SayHello",
            metadata = GrpcMetadata(
                path = "/com.example.GreeterService/SayHello",
                serviceName = "GreeterService",
                methodName = "SayHello",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY
            )
        )

        val result = CurlFormatter.formatAll(listOf(httpEndpoint, grpcEndpoint), "localhost:8080")

        assertTrue(result.contains("curl"))
        assertTrue(result.contains("grpcurl"))
        assertTrue(result.contains("## Get User"))
        assertTrue(result.contains("## SayHello"))
    }

    // ===== Options tests =====

    @Test
    fun testDefaultOptionsMatchesCurrentOutput() {
        val getEndpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(path = "/api/users/1", method = HttpMethod.GET)
        )
        val postEndpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                contentType = "application/json",
                parameters = listOf(
                    ApiParameter(name = "name", binding = ParameterBinding.Body, example = "John")
                )
            )
        )
        // Default options == no options (backward compat)
        assertEquals(CurlFormatter.format(getEndpoint, "http://localhost"), CurlFormatter.format(getEndpoint, "http://localhost", CurlFormatOptions()))
        assertEquals(CurlFormatter.format(postEndpoint, "http://localhost"), CurlFormatter.format(postEndpoint, "http://localhost", CurlFormatOptions()))
        assertEquals(
            CurlFormatter.formatAll(listOf(getEndpoint, postEndpoint), "http://localhost"),
            CurlFormatter.formatAll(listOf(getEndpoint, postEndpoint), "http://localhost", CurlFormatOptions())
        )
    }

    @Test
    fun testLongFlags() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                contentType = "application/json",
                headers = listOf(ApiHeader("Authorization", "Bearer token")),
                parameters = listOf(
                    ApiParameter(name = "name", binding = ParameterBinding.Body, example = "John")
                )
            )
        )
        val result = CurlFormatter.format(endpoint, "http://localhost", CurlFormatOptions(longFlags = true))
        assertTrue("should contain --request", result.contains("--request"))
        assertTrue("should contain --header", result.contains("--header"))
        assertTrue("should contain --data", result.contains("--data"))
        assertFalse("should not contain -X ", result.contains("-X "))
        assertFalse("should not contain -H '", result.contains("-H '"))
        assertFalse("should not contain -d '", result.contains("-d '"))
    }

    @Test
    fun testLongFlagsMultipart() {
        val endpoint = ApiEndpoint(
            name = "Upload",
            metadata = httpMetadata(
                path = "/api/upload",
                method = HttpMethod.POST,
                contentType = "multipart/form-data",
                parameters = listOf(
                    ApiParameter(name = "file", binding = ParameterBinding.Form, example = "@/path")
                )
            )
        )
        val result = CurlFormatter.format(endpoint, "http://localhost", CurlFormatOptions(longFlags = true))
        assertTrue("should contain --form", result.contains("--form"))
        assertFalse("should not contain -F '", result.contains("-F '"))
    }

    @Test
    fun testPrettyPrintBody() {
        val body = ObjectModel.Object(
            mapOf("name" to FieldModel(ObjectModel.single("string")))
        )
        val endpoint = ApiEndpoint(
            name = "Create",
            metadata = httpMetadata(
                path = "/api/create",
                method = HttpMethod.POST,
                contentType = "application/json",
                body = body
            )
        )
        val result = CurlFormatter.format(endpoint, "http://localhost", CurlFormatOptions(prettyPrintBody = true))
        assertTrue("pretty JSON should contain newline+indent: $result", result.contains("\n  \""))
    }

    @Test
    fun testMultiLineFormat() {
        val endpoint = ApiEndpoint(
            name = "Get",
            metadata = httpMetadata(
                path = "/api/users/1",
                method = HttpMethod.GET,
                headers = listOf(ApiHeader("Authorization", "Bearer token"))
            )
        )
        val result = CurlFormatter.format(endpoint, "http://localhost", CurlFormatOptions(multiLineFormat = true))
        assertTrue("should contain line continuation: $result", result.contains(" \\\n  "))
    }

    @Test
    fun testIncludeCommentsFalse() {
        val e1 = ApiEndpoint(name = "A", metadata = httpMetadata(path = "/a", method = HttpMethod.GET))
        val e2 = ApiEndpoint(name = "B", metadata = httpMetadata(path = "/b", method = HttpMethod.GET))
        val result = CurlFormatter.formatAll(listOf(e1, e2), "http://localhost", CurlFormatOptions(includeComments = false))
        assertFalse("should not contain ##: $result", result.contains("##"))
        assertFalse("should not contain ---: $result", result.contains("---"))
        assertTrue("should contain curl", result.contains("curl"))
    }

    @Test
    fun testIncludeResponseExample() {
        val respBody = ObjectModel.Object(
            mapOf("id" to FieldModel(ObjectModel.single("int")))
        )
        val endpoint = ApiEndpoint(
            name = "Get",
            metadata = httpMetadata(
                path = "/api/users/1",
                method = HttpMethod.GET,
                responseBody = respBody
            )
        )
        val result = CurlFormatter.format(endpoint, "http://localhost", CurlFormatOptions(includeResponseExample = true))
        assertTrue("should contain # Response: $result", result.contains("# Response:"))

        // gRPC should NOT have response example (no-op)
        val grpcEndpoint = ApiEndpoint(
            name = "SayHello",
            metadata = GrpcMetadata(
                path = "/com.example.Greeter/SayHello",
                serviceName = "Greeter",
                methodName = "SayHello",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY
            )
        )
        val grpcResult = CurlFormatter.format(grpcEndpoint, "localhost:50051", CurlFormatOptions(includeResponseExample = true))
        assertFalse("gRPC should not have response example: $grpcResult", grpcResult.contains("# Response:"))
    }

    @Test
    fun testGrpcWithDefaultOptions() {
        val endpoint = ApiEndpoint(
            name = "SayHello",
            metadata = GrpcMetadata(
                path = "/com.example.Greeter/SayHello",
                serviceName = "Greeter",
                methodName = "SayHello",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY
            )
        )
        val defaultResult = CurlFormatter.format(endpoint, "localhost:50051")
        val optionsResult = CurlFormatter.format(endpoint, "localhost:50051", CurlFormatOptions())
        assertEquals(defaultResult, optionsResult)
    }

    // ===== Additional edge-case tests =====

    @Test
    fun testLongFlagsAndMultiLineCombo() {
        val endpoint = ApiEndpoint(
            name = "Create",
            metadata = httpMetadata(
                path = "/api/create",
                method = HttpMethod.POST,
                contentType = "application/json",
                headers = listOf(ApiHeader("Authorization", "Bearer token")),
                parameters = listOf(
                    ApiParameter(name = "name", binding = ParameterBinding.Body, example = "John")
                )
            )
        )
        val result = CurlFormatter.format(
            endpoint, "http://localhost",
            CurlFormatOptions(longFlags = true, multiLineFormat = true)
        )
        assertTrue("should use long flags: $result", result.contains("--request"))
        assertTrue("should use line continuation: $result", result.contains(" \\\n  "))
        assertFalse("should not use short -X: $result", result.contains("-X "))
    }

    @Test
    fun testPrettyPrintAndCommentsCombo() {
        val body = ObjectModel.Object(
            mapOf("name" to FieldModel(ObjectModel.single("string")))
        )
        val endpoint = ApiEndpoint(
            name = "Create",
            metadata = httpMetadata(
                path = "/api/create",
                method = HttpMethod.POST,
                contentType = "application/json",
                body = body
            )
        )
        // Comments (## name) are only added by formatAll, not format
        val result = CurlFormatter.formatAll(
            listOf(endpoint), "http://localhost",
            CurlFormatOptions(includeComments = true, prettyPrintBody = true)
        )
        assertTrue("should have comments: $result", result.contains("## Create"))
        assertTrue("should have pretty JSON: $result", result.contains("\n  \""))
    }

    @Test
    fun testAllOptionsEnabled() {
        val body = ObjectModel.Object(
            mapOf("name" to FieldModel(ObjectModel.single("string")))
        )
        val respBody = ObjectModel.Object(
            mapOf("id" to FieldModel(ObjectModel.single("int")))
        )
        val endpoint = ApiEndpoint(
            name = "Create",
            metadata = httpMetadata(
                path = "/api/create",
                method = HttpMethod.POST,
                contentType = "application/json",
                headers = listOf(ApiHeader("X-Custom", "value")),
                body = body,
                responseBody = respBody
            )
        )
        // Comments (## name) are only added by formatAll, not format
        val result = CurlFormatter.formatAll(
            listOf(endpoint), "http://localhost",
            CurlFormatOptions(
                includeComments = true,
                prettyPrintBody = true,
                multiLineFormat = true,
                longFlags = true,
                includeResponseExample = true,
            )
        )
        assertTrue("comments: $result", result.contains("## Create"))
        assertTrue("pretty JSON: $result", result.contains("\n  \""))
        assertTrue("multi-line: $result", result.contains(" \\\n  "))
        assertTrue("long flags: $result", result.contains("--request"))
        assertTrue("response example: $result", result.contains("# Response:"))
    }

    @Test
    fun testResolvedBodyJsonFromExtensions() {
        // When extensions contain RESOLVED_BODY_JSON_KEY, the formatter should use
        // that JSON instead of serializing the ObjectModel body.
        val body = ObjectModel.Object(
            mapOf("name" to FieldModel(ObjectModel.single("string")))
        )
        val extensions = com.itangcent.easyapi.exporter.model.MutableExtension()
        extensions[EndpointVariableResolver.RESOLVED_BODY_JSON_KEY] = """{"name":"alice"}"""

        val endpoint = ApiEndpoint(
            name = "Create",
            metadata = httpMetadata(
                path = "/api/create",
                method = HttpMethod.POST,
                contentType = "application/json",
                body = body
            ),
            extensions = extensions
        )
        val result = CurlFormatter.format(endpoint, "http://localhost")
        assertTrue("should use resolved body JSON: $result", result.contains("alice"))
    }

    @Test
    fun testFormatWithEmptyHost() {
        val endpoint = ApiEndpoint(
            name = "Get",
            metadata = httpMetadata(path = "/api/test", method = HttpMethod.GET)
        )
        val result = CurlFormatter.format(endpoint, "")
        assertTrue("should still contain curl: $result", result.contains("curl"))
    }

    @Test
    fun testFormatAllWithOptionsAndMultipleEndpoints() {
        val e1 = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/1",
                method = HttpMethod.GET,
                headers = listOf(ApiHeader("Auth", "token"))
            )
        )
        val e2 = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                contentType = "application/json",
                parameters = listOf(
                    ApiParameter(name = "name", binding = ParameterBinding.Body, example = "John")
                )
            )
        )
        val result = CurlFormatter.formatAll(
            listOf(e1, e2), "http://localhost",
            CurlFormatOptions(longFlags = true, multiLineFormat = true)
        )
        assertTrue("should contain both names: $result", result.contains("## Get User"))
        assertTrue("should contain both names: $result", result.contains("## Create User"))
        assertTrue("should use long flags: $result", result.contains("--request"))
        assertTrue("should use multi-line: $result", result.contains(" \\\n  "))
    }

    @Test
    fun testPrettyPrintInvalidJsonFallsBackToCompact() {
        // If the body serializes to invalid JSON, prettyJson should fall back to
        // the compact string rather than throwing.
        val extensions = com.itangcent.easyapi.exporter.model.MutableExtension()
        extensions[EndpointVariableResolver.RESOLVED_BODY_JSON_KEY] = "not valid json"

        val endpoint = ApiEndpoint(
            name = "Create",
            metadata = httpMetadata(
                path = "/api/create",
                method = HttpMethod.POST,
                contentType = "application/json",
                body = ObjectModel.Object(emptyMap())
            ),
            extensions = extensions
        )
        val result = CurlFormatter.format(
            endpoint, "http://localhost",
            CurlFormatOptions(prettyPrintBody = true)
        )
        // Should not throw, should still produce a curl command
        assertTrue("should contain curl: $result", result.contains("curl"))
        assertTrue("should contain the invalid json: $result", result.contains("not valid json"))
    }

    @Test
    fun testNoContentTypeNoHeader() {
        val endpoint = ApiEndpoint(
            name = "Get",
            metadata = httpMetadata(path = "/api/test", method = HttpMethod.GET)
        )
        val result = CurlFormatter.format(endpoint, "http://localhost")
        assertFalse("should not contain Content-Type: $result", result.contains("Content-Type"))
    }

    @Test
    fun testGrpcWithLongFlags() {
        // gRPC formatting should not be affected by longFlags (grpcurl uses its own flags)
        val endpoint = ApiEndpoint(
            name = "SayHello",
            metadata = GrpcMetadata(
                path = "/com.example.Greeter/SayHello",
                serviceName = "Greeter",
                methodName = "SayHello",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY
            )
        )
        val result = CurlFormatter.format(
            endpoint, "localhost:50051",
            CurlFormatOptions(longFlags = true)
        )
        assertTrue("should contain grpcurl: $result", result.contains("grpcurl"))
        assertFalse("should not contain --request: $result", result.contains("--request"))
    }
}
