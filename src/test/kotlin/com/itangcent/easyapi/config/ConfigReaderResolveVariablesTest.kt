package com.itangcent.easyapi.config

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.*

class ConfigReaderResolveVariablesTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var configReader: TestConfigReader

    override fun setUp() {
        super.setUp()
        configReader = TestConfigReader.empty(project)
    }

    fun testResolveDollarBraceSyntax() {
        configReader = TestConfigReader.fromRules(project, "host" to "api.example.com")
        val result = configReader.resolveVariables("https://\${host}/users")
        assertEquals("https://api.example.com/users", result)
    }

    fun testResolveDoubleBraceSyntax() {
        configReader = TestConfigReader.fromRules(project, "host" to "api.example.com")
        val result = configReader.resolveVariables("https://{{host}}/users")
        assertEquals("https://api.example.com/users", result)
    }

    fun testResolveBothSyntaxesInSameString() {
        configReader = TestConfigReader.fromRules(project, "host" to "api.example.com", "port" to "8080")
        val result = configReader.resolveVariables("https://\${host}:{{port}}/users")
        assertEquals("https://api.example.com:8080/users", result)
    }

    fun testLeaveUnresolvedPlaceholdersUnchanged() {
        configReader = TestConfigReader.fromRules(project, "host" to "api.example.com")
        val result = configReader.resolveVariables("https://\${host}/{{unknown}}")
        assertEquals("https://api.example.com/{{unknown}}", result)
    }

    fun testReturnInputUnchangedWhenNoPlaceholders() {
        configReader = TestConfigReader.empty(project)
        val result = configReader.resolveVariables("https://api.example.com/users")
        assertEquals("https://api.example.com/users", result)
    }

    fun testHandleMultipleSameVariable() {
        configReader = TestConfigReader.fromRules(project, "token" to "abc123")
        val result = configReader.resolveVariables("Bearer \${token} and {{token}}")
        assertEquals("Bearer abc123 and abc123", result)
    }

    fun testHandleEmptyInput() {
        configReader = TestConfigReader.empty(project)
        val result = configReader.resolveVariables("")
        assertEquals("", result)
    }

    fun testTrimWhitespaceInVariableName() {
        configReader = TestConfigReader.fromRules(project, "host" to "api.example.com")
        val result = configReader.resolveVariables("https://\${ host }/users")
        assertEquals("https://api.example.com/users", result)
    }

    fun testResolveVariablesInHeaderValue() {
        configReader = TestConfigReader.fromRules(project, "auth_token" to "Bearer xyz789")
        val result = configReader.resolveVariables("\${auth_token}")
        assertEquals("Bearer xyz789", result)
    }

    fun testResolveVariablesInJsonBody() {
        configReader = TestConfigReader.fromRules(project, "user_id" to "12345", "api_key" to "secret123")
        val result = configReader.resolveVariables("{\"userId\": \${user_id}, \"apiKey\": \"{{api_key}}\"}")
        assertEquals("{\"userId\": 12345, \"apiKey\": \"secret123\"}", result)
    }

    fun testResolveMultipleDifferentVariables() {
        configReader = TestConfigReader.fromRules(project, 
            "scheme" to "https",
            "host" to "api.example.com",
            "port" to "8080",
            "path" to "v1/users"
        )
        val result = configReader.resolveVariables("\${scheme}://{{host}}:\${port}/{{path}}")
        assertEquals("https://api.example.com:8080/v1/users", result)
    }

    fun testHandleVariableWithUnderscoreAndDotsInName() {
        configReader = TestConfigReader.fromRules(project, "api.gateway.host" to "gateway.example.com")
        val result = configReader.resolveVariables("https://\${api.gateway.host}/api")
        assertEquals("https://gateway.example.com/api", result)
    }
}
