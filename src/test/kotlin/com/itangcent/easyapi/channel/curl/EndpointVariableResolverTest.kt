package com.itangcent.easyapi.channel.curl

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ApiHeader
import com.itangcent.easyapi.core.export.ApiParameter
import com.itangcent.easyapi.core.export.GrpcMetadata
import com.itangcent.easyapi.core.export.GrpcStreamingType
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.psi.model.ObjectModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit tests for [EndpointVariableResolver] (no IntelliJ fixture required).
 */
class EndpointVariableResolverTest {

    private val emptyFallback: (String) -> String? = { null }

    @Test
    fun `resolves double-brace placeholders from variables map`() {
        val endpoint = httpEndpoint(path = "/api/{{userId}}")
        val r = EndpointVariableResolver.resolve(
            endpoint = endpoint,
            host = "http://localhost",
            variables = mapOf("userId" to "42"),
            fallback = emptyFallback,
        )
        assertEquals("/api/42", (r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata).path)
        assertTrue("userId" in r.resolved)
        assertTrue("userId" !in r.missing)
    }

    @Test
    fun `resolves dollar-brace placeholders from variables map`() {
        val endpoint = httpEndpoint(path = "/api/\${userId}")
        val r = EndpointVariableResolver.resolve(endpoint, "http://localhost", mapOf("userId" to "42"), emptyFallback)
        assertEquals("/api/42", (r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata).path)
    }

    @Test
    fun `fallback lambda is called for vars not in map`() {
        val endpoint = httpEndpoint(path = "/{{token}}")
        val fallback: (String) -> String? = { k -> if (k == "token") "abc123" else null }
        val r = EndpointVariableResolver.resolve(endpoint, "h", emptyMap(), fallback)
        assertEquals("/abc123", (r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata).path)
        assertTrue("token" in r.resolved)
    }

    @Test
    fun `unresolved vars stay as-is and appear in missing`() {
        val endpoint = httpEndpoint(path = "/{{unknown}}")
        val r = EndpointVariableResolver.resolve(endpoint, "h", emptyMap(), emptyFallback)
        assertEquals("/{{unknown}}", (r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata).path)
        assertTrue("unknown" in r.missing)
        assertFalse("unknown" in r.resolved)
    }

    @Test
    fun `host is resolved`() {
        val endpoint = httpEndpoint(path = "/api")
        val r = EndpointVariableResolver.resolve(endpoint, "http://{{host}}", mapOf("host" to "example.com"), emptyFallback)
        assertEquals("http://example.com", r.host)
    }

    @Test
    fun `HttpMetadata headers are resolved`() {
        val endpoint = ApiEndpoint(
            name = "t",
            metadata = httpMetadata(
                path = "/api",
                method = HttpMethod.GET,
                headers = listOf(
                    ApiHeader(name = "X-User", value = "{{user}}"),
                    ApiHeader(name = "{{hdr}}", value = "v"),
                ),
            ),
        )
        val r = EndpointVariableResolver.resolve(endpoint, "h", mapOf("user" to "alice", "hdr" to "X-Trace-Id"), emptyFallback)
        val headers = (r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata).headers
        assertEquals("alice", headers[0].value)
        assertEquals("X-Trace-Id", headers[1].name)
    }

    @Test
    fun `HttpMetadata parameters name example default are resolved`() {
        val endpoint = ApiEndpoint(
            name = "t",
            metadata = httpMetadata(
                path = "/api",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(name = "{{p}}", example = "{{ex}}", defaultValue = "{{df}}", binding = ParameterBinding.Query),
                ),
            ),
        )
        val r = EndpointVariableResolver.resolve(endpoint, "h", mapOf("p" to "page", "ex" to "1", "df" to "0"), emptyFallback)
        val param = (r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata).parameters[0]
        assertEquals("page", param.name)
        assertEquals("1", param.example)
        assertEquals("0", param.defaultValue)
    }

    @Test
    fun `HttpMetadata contentType is resolved`() {
        val endpoint = ApiEndpoint(
            name = "t",
            metadata = httpMetadata(path = "/api", method = HttpMethod.POST, contentType = "application/{{fmt}}"),
        )
        val r = EndpointVariableResolver.resolve(endpoint, "h", mapOf("fmt" to "json"), emptyFallback)
        assertEquals("application/json", (r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata).contentType)
    }

    @Test
    fun `GrpcMetadata fields are resolved`() {
        val endpoint = ApiEndpoint(
            name = "g",
            metadata = GrpcMetadata(
                path = "/{{pkg}}.{{svc}}/{{m}}",
                serviceName = "{{svc}}",
                methodName = "{{m}}",
                packageName = "{{pkg}}",
                streamingType = GrpcStreamingType.UNARY,
            ),
        )
        val r = EndpointVariableResolver.resolve(endpoint, "h", mapOf("pkg" to "demo", "svc" to "Greeter", "m" to "SayHello"), emptyFallback)
        val meta = r.endpoint.metadata as GrpcMetadata
        assertEquals("demo", meta.packageName)
        assertEquals("Greeter", meta.serviceName)
        assertEquals("SayHello", meta.methodName)
        assertEquals("/demo.Greeter/SayHello", meta.path)
    }

    @Test
    fun `original endpoint is not mutated`() {
        val original = httpEndpoint(path = "/{{x}}")
        EndpointVariableResolver.resolve(original, "h", mapOf("x" to "1"), emptyFallback)
        assertEquals("/{{x}}", (original.metadata as com.itangcent.easyapi.core.export.HttpMetadata).path)
    }

    @Test
    fun `body JSON is resolved and stashed in extensions`() {
        // Place placeholder in field NAME (serialized as-is by RawJsonHandler);
        // field VALUES use type-based defaults, so placeholders in Single(type) won't appear in JSON.
        val obj = ObjectModel.Object(
            linkedMapOf(
                "{{user}}" to com.itangcent.easyapi.core.psi.model.FieldModel(ObjectModel.Single("string")),
            ),
        )
        val endpoint = ApiEndpoint(
            name = "t",
            metadata = httpMetadata(path = "/api", method = HttpMethod.POST, body = obj),
        )
        val r = EndpointVariableResolver.resolve(endpoint, "h", mapOf("user" to "alice"), emptyFallback)
        val stashed = r.endpoint.extensions.exts[EndpointVariableResolver.RESOLVED_BODY_JSON_KEY] as? String
        assertNotNull("resolved body JSON should be stashed in extensions", stashed)
        assertTrue("stashed JSON should contain resolved value: $stashed", stashed!!.contains("alice"))
        assertFalse("stashed JSON should not contain placeholder: $stashed", stashed.contains("{{user}}"))
        assertTrue("user" in r.resolved)
    }

    @Test
    fun `string without placeholders is returned as-is`() {
        assertEquals("plain", EndpointVariableResolver.resolveString("plain", emptyMap(), emptyFallback))
    }

    @Test
    fun `empty variables map with null-returning fallback leaves all placeholders`() {
        val endpoint = httpEndpoint(path = "/{{a}}/{{b}}")
        val r = EndpointVariableResolver.resolve(endpoint, "h", emptyMap(), emptyFallback)
        assertEquals("/{{a}}/{{b}}", (r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata).path)
        assertEquals(setOf("a", "b"), r.missing)
        assertTrue(r.resolved.isEmpty())
    }

    @Test
    fun `null optional fields stay null after resolution`() {
        val endpoint = httpEndpoint(path = "/api", method = HttpMethod.GET)
        val r = EndpointVariableResolver.resolve(endpoint, "h", mapOf("x" to "1"), emptyFallback)
        val meta = r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata
        assertNull(meta.contentType)
    }

    @Test
    fun `does not mutate the original endpoint`() {
        val endpoint = httpEndpoint(path = "/api/{{userId}}")
        val originalPath = (endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata).path
        EndpointVariableResolver.resolve(endpoint, "h", mapOf("userId" to "42"), emptyFallback)
        // Original should be untouched
        assertEquals("/api/{{userId}}", originalPath)
        assertEquals("/api/{{userId}}", (endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata).path)
    }

    @Test
    fun `resolves mixed brace styles in same string`() {
        val endpoint = httpEndpoint(path = "/api/{{a}}/\${b}")
        val r = EndpointVariableResolver.resolve(
            endpoint, "h", mapOf("a" to "1", "b" to "2"), emptyFallback
        )
        val meta = r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata
        assertEquals("/api/1/2", meta.path)
        assertEquals(setOf("a", "b"), r.resolved)
        assertTrue(r.missing.isEmpty())
    }

    @Test
    fun `falls back to ConfigReader when variable not in map`() {
        val endpoint = httpEndpoint(path = "/api/{{host}}")
        val r = EndpointVariableResolver.resolve(
            endpoint, "h", emptyMap(), { k -> if (k == "host") "example.com" else null }
        )
        val meta = r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata
        assertEquals("/api/example.com", meta.path)
        assertTrue(r.resolved.contains("host"))
        assertTrue(r.missing.isEmpty())
    }

    @Test
    fun `variables map takes priority over fallback`() {
        val endpoint = httpEndpoint(path = "/api/{{env}}")
        val r = EndpointVariableResolver.resolve(
            endpoint, "h", mapOf("env" to "prod"), { _ -> "dev" }
        )
        val meta = r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata
        assertEquals("/api/prod", meta.path)
    }

    @Test
    fun `resolves placeholders in headers`() {
        val endpoint = ApiEndpoint(
            name = "t",
            metadata = httpMetadata(
                path = "/api",
                method = HttpMethod.GET,
                headers = listOf(com.itangcent.easyapi.core.export.ApiHeader("Authorization", "Bearer {{token}}"))
            )
        )
        val r = EndpointVariableResolver.resolve(
            endpoint, "h", mapOf("token" to "abc123"), emptyFallback
        )
        val meta = r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata
        assertEquals("Bearer abc123", meta.headers[0].value)
    }

    @Test
    fun `resolves placeholders in query parameter values`() {
        val endpoint = ApiEndpoint(
            name = "t",
            metadata = httpMetadata(
                path = "/api",
                method = HttpMethod.GET,
                parameters = listOf(
                    com.itangcent.easyapi.core.export.ApiParameter(
                        name = "filter",
                        defaultValue = "{{filterValue}}",
                        binding = com.itangcent.easyapi.core.export.ParameterBinding.Query
                    )
                )
            )
        )
        val r = EndpointVariableResolver.resolve(
            endpoint, "h", mapOf("filterValue" to "active"), emptyFallback
        )
        val meta = r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata
        assertEquals("active", meta.parameters[0].defaultValue)
    }

    @Test
    fun `partial resolution reports both resolved and missing`() {
        val endpoint = httpEndpoint(path = "/{{a}}/{{b}}/{{c}}")
        val r = EndpointVariableResolver.resolve(
            endpoint, "h", mapOf("a" to "1"), emptyFallback
        )
        assertEquals(setOf("a"), r.resolved)
        assertEquals(setOf("b", "c"), r.missing)
        val meta = r.endpoint.metadata as com.itangcent.easyapi.core.export.HttpMetadata
        assertEquals("/1/{{b}}/{{c}}", meta.path)
    }

    @Test
    fun `resolves placeholders in host string`() {
        val endpoint = httpEndpoint(path = "/api")
        val r = EndpointVariableResolver.resolve(
            endpoint, "http://{{host}}:{{port}}", mapOf("host" to "localhost", "port" to "8080"), emptyFallback
        )
        assertEquals("http://localhost:8080", r.host)
        assertEquals(setOf("host", "port"), r.resolved)
    }

    private fun httpEndpoint(path: String, method: HttpMethod = HttpMethod.GET): ApiEndpoint {
        return ApiEndpoint(name = "t", metadata = httpMetadata(path = path, method = method))
    }
}
