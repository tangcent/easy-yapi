package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.config.DOUBLE_BRACE_PATTERN
import com.itangcent.easyapi.config.DOLLAR_BRACE_PATTERN
import com.itangcent.easyapi.dashboard.script.DynamicVariables
import com.itangcent.easyapi.http.FormParam
import com.itangcent.easyapi.http.KeyValue
import org.junit.Assert.*
import org.junit.Test

class VariableResolutionTest {

    private val DYNAMIC_VARIABLE_PATTERN = Regex("\\{\\{\\$(\\w+)}}")

    private fun resolveVariables(input: String, envMap: Map<String, String> = emptyMap()): String {
        var result = input
        if (envMap.isNotEmpty()) {
            result = DOUBLE_BRACE_PATTERN.replace(result) { match ->
                val key = match.groupValues[1].trim()
                envMap[key] ?: match.value
            }
            result = DOLLAR_BRACE_PATTERN.replace(result) { match ->
                val key = match.groupValues[1].trim()
                envMap[key] ?: match.value
            }
        }
        result = DYNAMIC_VARIABLE_PATTERN.replace(result) { match ->
            val varName = match.groupValues[1]
            DynamicVariables.resolve(varName) ?: match.value
        }
        return result
    }

    @Test
    fun testResolveDoubleBraceEnvVar() {
        val envMap = mapOf("base_url" to "https://api.example.com")
        val result = resolveVariables("{{base_url}}/users", envMap)
        assertEquals("https://api.example.com/users", result)
    }

    @Test
    fun testResolveDollarBraceEnvVar() {
        val envMap = mapOf("token" to "abc123")
        val result = resolveVariables("Bearer \${token}", envMap)
        assertEquals("Bearer abc123", result)
    }

    @Test
    fun testResolveUnknownEnvVarLeftUnchanged() {
        val envMap = mapOf("known" to "value")
        val result = resolveVariables("{{unknown}}", envMap)
        assertEquals("{{unknown}}", result)
    }

    @Test
    fun testResolveNoEnvVarsSkipsEnvResolution() {
        val result = resolveVariables("{{base_url}}/users", emptyMap())
        assertEquals("{{base_url}}/users", result)
    }

    @Test
    fun testResolveDynamicTimestamp() {
        val result = resolveVariables("ts={{\$timestamp}}")
        assertFalse(result.contains("{{\$timestamp}}"))
        assertTrue(result.startsWith("ts="))
        assertTrue(result.substringAfter("ts=").toLong() > 0)
    }

    @Test
    fun testResolveDynamicRandomInt() {
        val result = resolveVariables("rid={{\$randomInt}}")
        assertFalse(result.contains("{{\$randomInt}}"))
        val value = result.substringAfter("rid=").toInt()
        assertTrue(value in 0..999)
    }

    @Test
    fun testResolveDynamicGuid() {
        val result = resolveVariables("id={{\$guid}}")
        assertFalse(result.contains("{{\$guid}}"))
        assertTrue(result.matches(Regex("id=[0-9a-f-]{36}")))
    }

    @Test
    fun testResolveDynamicRandomAlphaNumeric() {
        val result = resolveVariables("key={{\$randomAlphaNumeric}}")
        assertFalse(result.contains("{{\$randomAlphaNumeric}}"))
        val value = result.substringAfter("key=")
        assertEquals(8, value.length)
        assertTrue(value.all { it.isLetterOrDigit() })
    }

    @Test
    fun testResolveDynamicRandomFirstName() {
        val result = resolveVariables("name={{\$randomFirstName}}")
        assertFalse(result.contains("{{\$randomFirstName}}"))
        val value = result.substringAfter("name=")
        assertTrue(value in listOf("Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry"))
    }

    @Test
    fun testResolveDynamicRandomLastName() {
        val result = resolveVariables("name={{\$randomLastName}}")
        assertFalse(result.contains("{{\$randomLastName}}"))
        val value = result.substringAfter("name=")
        assertTrue(value in listOf("Smith", "Johnson", "Williams", "Brown", "Jones", "Davis", "Miller", "Wilson"))
    }

    @Test
    fun testResolveDynamicRandomEmail() {
        val result = resolveVariables("email={{\$randomEmail}}")
        assertFalse(result.contains("{{\$randomEmail}}"))
        val value = result.substringAfter("email=")
        assertTrue(value.contains("@"))
    }

    @Test
    fun testResolveDynamicRandomUrl() {
        val result = resolveVariables("url={{\$randomUrl}}")
        assertFalse(result.contains("{{\$randomUrl}}"))
        val value = result.substringAfter("url=")
        assertTrue(value.startsWith("https://example.com/"))
    }

    @Test
    fun testResolveDynamicRandomIP() {
        val result = resolveVariables("ip={{\$randomIP}}")
        assertFalse(result.contains("{{\$randomIP}}"))
        val value = result.substringAfter("ip=")
        val parts = value.split(".")
        assertEquals(4, parts.size)
        parts.forEach { part ->
            assertTrue(part.toInt() in 0..255)
        }
    }

    @Test
    fun testResolveDynamicRandomUuid() {
        val result = resolveVariables("uuid={{\$randomUuid}}")
        assertFalse(result.contains("{{\$randomUuid}}"))
        val value = result.substringAfter("uuid=")
        assertTrue(value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun testResolveUnknownDynamicVarLeftUnchanged() {
        val result = resolveVariables("val={{\$unknownVar}}")
        assertEquals("val={{\$unknownVar}}", result)
    }

    @Test
    fun testResolveMixedEnvAndDynamicVars() {
        val envMap = mapOf("base_url" to "https://api.example.com")
        val result = resolveVariables("{{base_url}}/users?ts={{\$timestamp}}&rid={{\$randomInt}}", envMap)
        assertTrue(result.startsWith("https://api.example.com/users?ts="))
        assertFalse(result.contains("{{\$timestamp}}"))
        assertFalse(result.contains("{{\$randomInt}}"))
        assertTrue(result.contains("&rid="))
    }

    @Test
    fun testResolveMultipleEnvVars() {
        val envMap = mapOf("host" to "https://api.example.com", "version" to "v1", "token" to "abc")
        val result = resolveVariables("{{host}}/{{version}}/users?token={{token}}", envMap)
        assertEquals("https://api.example.com/v1/users?token=abc", result)
    }

    @Test
    fun testResolveMultipleDynamicVars() {
        val result = resolveVariables("id={{\$guid}}&ts={{\$timestamp}}&rnd={{\$randomInt}}")
        assertFalse(result.contains("{{\$"))
        assertTrue(result.startsWith("id="))
        assertTrue(result.contains("&ts="))
        assertTrue(result.contains("&rnd="))
    }

    @Test
    fun testResolvePlainStringUnchanged() {
        val result = resolveVariables("plain text with no variables")
        assertEquals("plain text with no variables", result)
    }

    @Test
    fun testResolveEnvVarInHeader() {
        val envMap = mapOf("auth_token" to "Bearer my-jwt")
        val result = resolveVariables("{{auth_token}}", envMap)
        assertEquals("Bearer my-jwt", result)
    }

    @Test
    fun testResolveDynamicVarInHeader() {
        val result = resolveVariables("X-Request-Id: {{\$randomAlphaNumeric}}")
        assertTrue(result.startsWith("X-Request-Id: "))
        assertEquals(8, result.substringAfter(": ").length)
    }

    @Test
    fun testResolveEnvVarInBody() {
        val envMap = mapOf("user_id" to "42")
        val result = resolveVariables("""{"id": {{user_id}}}""", envMap)
        assertEquals("""{"id": 42}""", result)
    }

    @Test
    fun testResolveDynamicVarInBody() {
        val result = resolveVariables("""{"timestamp": "{{${'$'}timestamp}}"}""")
        assertFalse(result.contains("{{${'$'}timestamp}}"))
        assertTrue(result.contains("\"timestamp\":"))
    }

    @Test
    fun testResolveDollarBraceAndDoubleBraceTogether() {
        val envMap = mapOf("host" to "https://api.example.com", "token" to "abc123")
        val result = resolveVariables("{{host}}/users?token=\${token}", envMap)
        assertEquals("https://api.example.com/users?token=abc123", result)
    }

    @Test
    fun testResolveDynamicVarsWithoutEnvMap() {
        val result = resolveVariables("ts={{\$timestamp}}")
        assertFalse(result.contains("{{\$timestamp}}"))
        assertTrue(result.startsWith("ts="))
    }

    @Test
    fun testResolveEnvVarTrimsKeyWhitespace() {
        val envMap = mapOf("key" to "value")
        val result = resolveVariables("{{ key }}", envMap)
        assertEquals("value", result)
    }

    @Test
    fun testResolvePathParams() {
        val pathTemplate = "/users/{id}/posts/{postId}"
        val pathParams = listOf("id" to "42", "postId" to "100")
        var path = pathTemplate
        for ((key, value) in pathParams) {
            if (key.isNotEmpty() && value.isNotEmpty()) {
                path = path.replace("{$key}", java.net.URLEncoder.encode(value, "UTF-8"))
            }
        }
        assertEquals("/users/42/posts/100", path)
    }

    @Test
    fun testResolvePathParamsWithSpecialChars() {
        val pathTemplate = "/search/{query}"
        val pathParams = listOf("query" to "hello world")
        var path = pathTemplate
        for ((key, value) in pathParams) {
            if (key.isNotEmpty() && value.isNotEmpty()) {
                path = path.replace("{$key}", java.net.URLEncoder.encode(value, "UTF-8"))
            }
        }
        assertEquals("/search/hello+world", path)
    }

    @Test
    fun testResolvePathParamsSkipsEmpty() {
        val pathTemplate = "/users/{id}"
        val pathParams = listOf("id" to "")
        var path = pathTemplate
        for ((key, value) in pathParams) {
            if (key.isNotEmpty() && value.isNotEmpty()) {
                path = path.replace("{$key}", java.net.URLEncoder.encode(value, "UTF-8"))
            }
        }
        assertEquals("/users/{id}", path)
    }
}

class HttpRequestInputTest {

    @Test
    fun testDefaultConstruction() {
        val input = HttpRequestInput(
            host = "https://api.example.com",
            path = "/users",
            method = "GET"
        )
        assertEquals("https://api.example.com", input.host)
        assertEquals("/users", input.path)
        assertEquals("GET", input.method)
        assertTrue(input.pathParams.isEmpty())
        assertTrue(input.headers.isEmpty())
        assertTrue(input.query.isEmpty())
        assertNull(input.body)
        assertTrue(input.formParams.isEmpty())
        assertFalse(input.hasFormData)
        assertNull(input.contentType)
        assertNull(input.preRequestScript)
        assertNull(input.postResponseScript)
        assertEquals("", input.endpointName)
        assertEquals("", input.endpointKey)
        assertTrue(input.scriptScopes.isEmpty())
    }

    @Test
    fun testFullConstruction() {
        val input = HttpRequestInput(
            host = "https://api.example.com",
            path = "/users/{id}",
            method = "POST",
            pathParams = listOf("id" to "42"),
            headers = listOf(KeyValue("Authorization", "Bearer token")),
            query = listOf(KeyValue("page", "1")),
            body = """{"name":"Alice"}""",
            contentType = "application/json",
            preRequestScript = "pm.request.headers.add('X-Custom', 'value')",
            postResponseScript = "pm.test('status') { pm.expect(pm.response.code).to.eql(200) }",
            endpointName = "Create User",
            endpointKey = "user-controller-create"
        )
        assertEquals("POST", input.method)
        assertEquals(1, input.pathParams.size)
        assertEquals(1, input.headers.size)
        assertEquals(1, input.query.size)
        assertNotNull(input.body)
        assertNotNull(input.preRequestScript)
        assertNotNull(input.postResponseScript)
        assertEquals("Create User", input.endpointName)
    }

    @Test
    fun testFormDataConstruction() {
        val input = HttpRequestInput(
            host = "https://api.example.com",
            path = "/upload",
            method = "POST",
            formParams = listOf(FormParam.Text("field", "value")),
            hasFormData = true
        )
        assertTrue(input.hasFormData)
        assertEquals(1, input.formParams.size)
        assertNull(input.body)
    }
}

class RequestResultTest {

    @Test
    fun testSuccessResult() {
        val result = RequestResult(
            body = """{"id":1}""",
            isError = false,
            statusCode = 200,
            headers = listOf("Content-Type" to "application/json")
        )
        assertEquals("""{"id":1}""", result.body)
        assertFalse(result.isError)
        assertEquals(200, result.statusCode)
        assertEquals(1, result.headers.size)
        assertNull(result.testResults)
        assertFalse(result.requiresGrpcSetup)
    }

    @Test
    fun testErrorResult() {
        val result = RequestResult(
            body = "Not Found",
            isError = true,
            statusCode = 404
        )
        assertTrue(result.isError)
        assertEquals(404, result.statusCode)
    }

    @Test
    fun testResultWithTestResults() {
        val testResults = listOf(
            com.itangcent.easyapi.dashboard.script.TestResult("test1", true),
            com.itangcent.easyapi.dashboard.script.TestResult("test2", false, "failed")
        )
        val result = RequestResult(
            body = """{"ok":true}""",
            isError = false,
            statusCode = 200,
            testResults = testResults
        )
        assertNotNull(result.testResults)
        assertEquals(2, result.testResults!!.size)
        assertTrue(result.testResults!![0].passed)
        assertFalse(result.testResults!![1].passed)
    }

    @Test
    fun testGrpcSetupRequired() {
        val result = RequestResult(
            body = "gRPC client not available",
            isError = true,
            requiresGrpcSetup = true
        )
        assertTrue(result.requiresGrpcSetup)
    }
}
