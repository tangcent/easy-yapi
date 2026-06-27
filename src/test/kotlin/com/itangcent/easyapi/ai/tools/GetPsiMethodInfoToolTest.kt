package com.itangcent.easyapi.ai.tools

import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.ai.AiSettings
import com.itangcent.easyapi.ai.AiProvider
import com.itangcent.easyapi.ai.agent.AgentMemory
import com.itangcent.easyapi.ai.agent.ApprovalGate
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.util.json.GsonUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert

/**
 * Tests for [GetPsiMethodInfoTool].
 *
 * Verifies single-method lookup, overload disambiguation by `paramCount`,
 * the missing-class / missing-method error paths, and parameter validation.
 * Resolves classes via [com.intellij.psi.JavaPsiFacade.findClass] in the
 * project scope, so source files must be present in the fixture's VFS.
 */
class GetPsiMethodInfoToolTest : EasyApiLightCodeInsightFixtureTestCase() {

    private fun ctx(): ToolContext = ToolContext(
        project = project,
        configReader = ConfigReader.getInstance(project),
        aiSettings = AiSettings(
            provider = AiProvider.OPENAI,
            baseUrl = "", apiKey = "", model = "",
            requestTimeoutSec = 30, maxRequests = 8
        ),
        ruleFileResolver = RuleFileResolver(project),
        workingMemory = AgentMemory(),
        approvals = NoOpApprovalGate()
    )

    private fun addClasses() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/Calculator.java",
                """
                package com.example;

                public class Calculator {

                    public int add(int a, int b) {
                        return a + b;
                    }

                    public int add(int a, int b, int c) {
                        return a + b + c;
                    }

                    /**
                     * Multiplies two numbers.
                     */
                    public int multiply(int a, int b) {
                        return a * b;
                    }

                    private void secret() {}
                }
                """.trimIndent()
            )
        }
    }

    fun testReturnsInfoForSingleMethod() {
        addClasses()
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Calculator",
                    "methodName" to "multiply"
                ),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("should contain class name", text.contains("Calculator"))
        Assert.assertTrue("should contain method name", text.contains("multiply"))
        // The doc comment should be captured.
        Assert.assertTrue(
            "should capture doc comment text",
            text.contains("Multiplies") || text.contains("multiplies")
        )
    }

    fun testReturnsErrorWhenClassNotFound() {
        addClasses()
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.DoesNotExist",
                    "methodName" to "add"
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("not found")
        )
    }

    fun testReturnsErrorWhenMethodNotFound() {
        addClasses()
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Calculator",
                    "methodName" to "noSuchMethod"
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("not found")
        )
    }

    fun testParamCountDisambiguatesOverloads() {
        addClasses()
        // `add` is overloaded (2-arg + 3-arg); paramCount should narrow the match.
        val result2 = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Calculator",
                    "methodName" to "add",
                    "paramCount" to 2
                ),
                ctx()
            )
        }
        Assert.assertTrue(result2 is ToolResult.Text)
        val text2 = (result2 as ToolResult.Text).value
        // The 2-arg signature should have exactly 2 parameters listed.
        val params = GsonUtils.fromJson<Map<String, Any?>>(text2)["parameters"] as List<*>
        Assert.assertEquals("2-arg overload should have 2 params", 2, params.size)

        val result3 = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Calculator",
                    "methodName" to "add",
                    "paramCount" to 3
                ),
                ctx()
            )
        }
        Assert.assertTrue(result3 is ToolResult.Text)
        val text3 = (result3 as ToolResult.Text).value
        val params3 = GsonUtils.fromJson<Map<String, Any?>>(text3)["parameters"] as List<*>
        Assert.assertEquals("3-arg overload should have 3 params", 3, params3.size)
    }

    fun testRejectsMissingFqn() {
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf("methodName" to "add"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("missing required parameter")
        )
    }

    fun testRejectsMissingMethodName() {
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf("fqn" to "com.example.Calculator"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("missing required parameter")
        )
    }

    fun testRejectsBlankFqnAndMethodName() {
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf("fqn" to "  ", "methodName" to "  "),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Error)
    }

    fun testSignatureStringIncludesReturnTypeAndModifiers() {
        addClasses()
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Calculator",
                    "methodName" to "multiply"
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        val signature = GsonUtils.fromJson<Map<String, Any?>>(text)["signature"] as String
        // The signature should include the `public` modifier and the `int` return type.
        Assert.assertTrue(
            "signature should include modifier: $signature",
            signature.contains("public")
        )
        Assert.assertTrue(
            "signature should include return type: $signature",
            signature.contains("int")
        )
        Assert.assertTrue(
            "signature should include method name: $signature",
            signature.contains("multiply")
        )
    }

    fun testMethodWithoutDocCommentHasNullDocComment() {
        addClasses()
        // `add` has no doc comment — the `docComment` field should be null.
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Calculator",
                    "methodName" to "add",
                    "paramCount" to 2
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        val map = GsonUtils.fromJson<Map<String, Any?>>(text)
        // docComment is null when the method has no Javadoc — JSON serialises
        // it as null (absent in the object or explicit null).
        Assert.assertTrue(
            "docComment should be null when absent",
            map["docComment"] == null
        )
    }

    fun testIsRegisteredInStandardToolSet() {
        val names = standardRuleTools().map { it.name }
        Assert.assertTrue(
            "get_psi_method_info must be registered",
            names.contains("get_psi_method_info")
        )
    }

    fun testIsPerceptionTool() {
        Assert.assertEquals(ToolKind.PERCEPTION, GetPsiMethodInfoTool().kind)
    }

    fun testSchemaDeclaresRequiredParameters() {
        val schema = GetPsiMethodInfoTool().parametersSchema
        val required = schema["required"] as List<*>
        Assert.assertTrue("fqn must be required", required.contains("fqn"))
        Assert.assertTrue("methodName must be required", required.contains("methodName"))
    }

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}
