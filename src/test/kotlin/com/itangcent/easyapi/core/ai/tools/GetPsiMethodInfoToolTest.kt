package com.itangcent.easyapi.core.ai.tools

import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.core.ai.AiRuntimeConfig
import com.itangcent.easyapi.core.ai.AiProvider
import com.itangcent.easyapi.core.ai.agent.AgentMemory
import com.itangcent.easyapi.core.ai.agent.ApprovalGate
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.core.util.json.GsonUtils
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
        aiSettings = AiRuntimeConfig(
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

    // ------------------------------------------------------------------
    // Task 8 — type enrichment (returnType/returnTypeFqn/per-param typeFqn)
    // ------------------------------------------------------------------

    /**
     * Seeds a `Greeter` class with a `String` parameter and `String` return
     * type for type-enrichment assertions.
     */
    private fun addClassesForTypeEnrichment() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/Greeter.java",
                """
                package com.example;
                public class Greeter {
                    public String greet(String name) {
                        return "hello " + name;
                    }
                }
                """.trimIndent()
            )
        }
    }

    fun testParameterIncludesTypeFqn() {
        addClassesForTypeEnrichment()
        // Ensure java.lang.String is resolvable in the light fixture.
        loadJDKClass("java.lang.String")
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Greeter",
                    "methodName" to "greet"
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        val params = map["parameters"] as List<*>
        val nameParam = params.first {
            (it as Map<*, *>)["name"] == "name"
        } as Map<*, *>
        Assert.assertEquals(
            "String parameter should resolve typeFqn to java.lang.String",
            "java.lang.String",
            nameParam["typeFqn"]
        )
    }

    fun testReturnTypeFqnResolved() {
        addClassesForTypeEnrichment()
        // Ensure java.lang.String is resolvable in the light fixture.
        loadJDKClass("java.lang.String")
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Greeter",
                    "methodName" to "greet"
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        Assert.assertEquals(
            "String return type should resolve returnTypeFqn to java.lang.String",
            "java.lang.String",
            map["returnTypeFqn"]
        )
    }

    fun testReturnTypeAndReturnTypeFqnBothPresent() {
        addClassesForTypeEnrichment()
        loadJDKClass("java.lang.String")
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Greeter",
                    "methodName" to "greet"
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        // Both the additive returnType (presentableText) and returnTypeFqn
        // should be present. signature stays unchanged.
        Assert.assertEquals("String", map["returnType"])
        Assert.assertEquals("java.lang.String", map["returnTypeFqn"])
        val signature = map["signature"] as String
        Assert.assertTrue(
            "signature should still contain String: $signature",
            signature.contains("String")
        )
    }

    // ------------------------------------------------------------------
    // Task 9 — detail / maxBodyChars / body (REQ-3 core)
    // ------------------------------------------------------------------

    /**
     * Seeds a class with: a normal method body (`multiply`), an abstract
     * method (`abstractMethod`), and an empty-body method (`emptyMethod`).
     */
    private fun addClassesForBodyExtraction() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/Calculator.java",
                """
                package com.example;

                public class Calculator {

                    public int multiply(int a, int b) {
                        return a * b;
                    }

                    public void emptyMethod() {}

                    public abstract void abstractMethod();
                }
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/LongBody.java",
                """
                package com.example;

                public class LongBody {
                    public void longMethod() {
                        ${"// padding line\n                        ".repeat(20)}int x = 1;
                    }
                }
                """.trimIndent()
            )
        }
    }

    fun testSignatureDetailHasNoBodyField() {
        addClasses()
        // Default call (no `detail` arg) → no `body` key in JSON.
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
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        Assert.assertFalse(
            "default detail (signature) should NOT include body key",
            map.containsKey("body")
        )
    }

    fun testExplicitSignatureDetailHasNoBodyField() {
        addClasses()
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Calculator",
                    "methodName" to "multiply",
                    "detail" to "signature"
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        Assert.assertFalse(
            "detail=signature should NOT include body key",
            map.containsKey("body")
        )
    }

    fun testFullDetailIncludesBody() {
        addClassesForBodyExtraction()
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Calculator",
                    "methodName" to "multiply",
                    "detail" to "full"
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        Assert.assertEquals(
            "detail=full should include the stripped body",
            "return a * b;",
            map["body"]
        )
    }

    fun testFullDetailBodyNullForAbstractMethod() {
        addClassesForBodyExtraction()
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Calculator",
                    "methodName" to "abstractMethod",
                    "detail" to "full"
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        Assert.assertNull(
            "abstract method body should be null",
            map["body"]
        )
    }

    fun testFullDetailBodyEmptyStringForEmptyBody() {
        addClassesForBodyExtraction()
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Calculator",
                    "methodName" to "emptyMethod",
                    "detail" to "full"
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        Assert.assertEquals(
            "empty body {} should produce body=\"\"",
            "",
            map["body"]
        )
    }

    fun testMaxBodyCharsTruncatesBodyWithSuffix() {
        addClassesForBodyExtraction()
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.LongBody",
                    "methodName" to "longMethod",
                    "detail" to "full",
                    "maxBodyChars" to 50
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        val body = map["body"] as String
        Assert.assertTrue(
            "body should be truncated to ≤ 50 chars: actual length=${body.length}",
            body.length <= 50
        )
        Assert.assertTrue(
            "body should end with truncation suffix: $body",
            body.endsWith("... (truncated)")
        )
    }

    fun testMaxBodyCharsZeroUsesDefault() {
        addClassesForBodyExtraction()
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Calculator",
                    "methodName" to "multiply",
                    "detail" to "full",
                    "maxBodyChars" to 0
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        // With default 4000, the short body fits without truncation.
        Assert.assertEquals(
            "maxBodyChars=0 should fall back to default (no truncation)",
            "return a * b;",
            map["body"]
        )
    }

    fun testMaxBodyCharsNegativeUsesDefault() {
        addClassesForBodyExtraction()
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Calculator",
                    "methodName" to "multiply",
                    "detail" to "full",
                    "maxBodyChars" to -1
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        Assert.assertEquals(
            "maxBodyChars=-1 should fall back to default (no truncation)",
            "return a * b;",
            map["body"]
        )
    }

    fun testInvalidDetailValueDefaultsToSignature() {
        addClasses()
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "com.example.Calculator",
                    "methodName" to "multiply",
                    "detail" to "bogus"
                ),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        Assert.assertFalse(
            "invalid detail value should default to signature (no body key)",
            map.containsKey("body")
        )
    }

    fun testSchemaDeclaresDetailAndMaxBodyChars() {
        val schema = GetPsiMethodInfoTool().parametersSchema
        val props = schema["properties"] as Map<*, *>
        Assert.assertTrue("should declare detail", props.containsKey("detail"))
        Assert.assertTrue("should declare maxBodyChars", props.containsKey("maxBodyChars"))
        val detailSchema = props["detail"] as Map<*, *>
        val enumValues = detailSchema["enum"] as List<*>
        Assert.assertTrue("detail enum should include signature", enumValues.contains("signature"))
        Assert.assertTrue("detail enum should include full", enumValues.contains("full"))
    }

    // ------------------------------------------------------------------
    // Task 10 — tolerance + context parameter
    // ------------------------------------------------------------------

    /**
     * Seeds two `Calculator` classes in different packages plus a `Caller`
     * class that imports `com.example.Calculator`, for tolerance tests.
     */
    private fun addClassesForTolerance() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/Calculator.java",
                """
                package com.example;
                public class Calculator {
                    public int multiply(int a, int b) { return a * b; }
                }
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/dto/Calculator.java",
                """
                package com.example.dto;
                public class Calculator {
                    public int multiply(int a, int b) { return a * b; }
                }
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/Caller.java",
                """
                package com.example;
                import com.example.Calculator;
                public class Caller {
                    public Calculator calc;
                }
                """.trimIndent()
            )
        }
    }

    fun testAcceptsSimpleNameForFqn() {
        // Seed only one Calculator — simple name resolves via PsiShortNamesCache.
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/Calculator.java",
                """
                package com.example;
                public class Calculator {
                    public int multiply(int a, int b) { return a * b; }
                }
                """.trimIndent()
            )
        }
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "Calculator",
                    "methodName" to "multiply"
                ),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        Assert.assertEquals("com.example.Calculator", map["className"])
    }

    fun testAcceptsSimpleNameWithContext() {
        addClassesForTolerance()
        // Simple name "Calculator" + context "com.example.Caller" → Caller's
        // imports resolve to com.example.Calculator.
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "Calculator",
                    "methodName" to "multiply",
                    "context" to "com.example.Caller"
                ),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        Assert.assertEquals(
            "context should resolve Calculator to com.example.Calculator",
            "com.example.Calculator",
            map["className"]
        )
    }

    fun testAmbiguousSimpleNameReturnsError() {
        addClassesForTolerance()
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "Calculator",
                    "methodName" to "multiply"
                ),
                ctx()
            )
        }
        Assert.assertTrue("expected Error result, got $result", result is ToolResult.Error)
        val msg = (result as ToolResult.Error).message
        Assert.assertTrue(
            "error should mention find_classes_by_name: $msg",
            msg.contains("find_classes_by_name")
        )
    }

    fun testContextUnresolvableFallsBackSilently() {
        // Seed only one Calculator — bogus context falls back to no-context.
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/Calc.java",
                """
                package com.example;
                public class Calc {
                    public int multiply(int a, int b) { return a * b; }
                }
                """.trimIndent()
            )
        }
        val result = runBlocking {
            GetPsiMethodInfoTool().execute(
                mapOf(
                    "fqn" to "Calc",
                    "methodName" to "multiply",
                    "context" to "bogus.not.a.real.fqn.or.path"
                ),
                ctx()
            )
        }
        Assert.assertTrue(
            "bogus context should fall back silently: $result",
            result is ToolResult.Text
        )
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        Assert.assertEquals("com.example.Calc", map["className"])
    }

    fun testSchemaDeclaresContextProperty() {
        val schema = GetPsiMethodInfoTool().parametersSchema
        val props = schema["properties"] as Map<*, *>
        Assert.assertTrue("should declare context", props.containsKey("context"))
        // context must be optional — `required` must NOT contain it.
        val required = schema["required"] as List<*>
        Assert.assertFalse(
            "context must be optional",
            required.contains("context")
        )
    }

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}
