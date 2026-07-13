package com.itangcent.easyapi.ai.tools

import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.ai.AiRuntimeConfig
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
 * Tests for [FindClassesByNameTool].
 *
 * Covers the 12-case test plan from the spec: simple-name resolution, FQN
 * short-circuit, batch mode, multiple-match sorting, context-preferred
 * match ordering, missing/blank parameter rejection, registration, kind,
 * and schema shape.
 *
 * Uses the light fixture's VFS — the tool resolves classes via
 * [com.intellij.psi.search.PsiShortNamesCache] in the project scope.
 */
class FindClassesByNameToolTest : EasyApiLightCodeInsightFixtureTestCase() {

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
                "com/example/AuthResponse.java",
                "package com.example; public class AuthResponse {}"
            )
            myFixture.addFileToProject(
                "com/example/User.java",
                "package com.example; public class User {}"
            )
        }
    }

    // --- single-name resolution ---

    fun testResolvesSimpleNameToFqn() {
        addClasses()
        val result = runBlocking {
            FindClassesByNameTool().execute(mapOf("name" to "AuthResponse"), ctx())
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val fqns: List<String> = GsonUtils.fromJson((result as ToolResult.Text).value)
        Assert.assertTrue(
            "should find com.example.AuthResponse",
            fqns.contains("com.example.AuthResponse")
        )
    }

    fun testReturnsEmptyArrayForNoMatch() {
        addClasses()
        val result = runBlocking {
            FindClassesByNameTool().execute(mapOf("name" to "DoesNotExist"), ctx())
        }
        Assert.assertTrue(result is ToolResult.Text)
        Assert.assertEquals("[]", (result as ToolResult.Text).value)
    }

    // --- FQN short-circuit ---

    fun testFqnShortCircuitReturnsSingleElement() {
        addClasses()
        val result = runBlocking {
            FindClassesByNameTool().execute(
                mapOf("name" to "com.example.AuthResponse"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val fqns: List<String> = GsonUtils.fromJson((result as ToolResult.Text).value)
        Assert.assertEquals("FQN short-circuit should return single element", 1, fqns.size)
        Assert.assertEquals("com.example.AuthResponse", fqns[0])
    }

    fun testFqnShortCircuitReturnsEmptyForUnknownFqn() {
        addClasses()
        val result = runBlocking {
            FindClassesByNameTool().execute(
                mapOf("name" to "com.example.Missing"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        Assert.assertEquals("[]", (result as ToolResult.Text).value)
    }

    // --- batch mode ---

    fun testBatchReturnsMapOfNamesToFqns() {
        addClasses()
        val result = runBlocking {
            FindClassesByNameTool().execute(
                mapOf("names" to listOf("AuthResponse", "User")),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        // Batch mode returns a JSON object mapping each name to its array.
        Assert.assertTrue("should contain AuthResponse key", text.contains("\"AuthResponse\""))
        Assert.assertTrue("should contain User key", text.contains("\"User\""))
        Assert.assertTrue("should find com.example.AuthResponse", text.contains("com.example.AuthResponse"))
        Assert.assertTrue("should find com.example.User", text.contains("com.example.User"))
    }

    // --- multiple matches + sorting ---

    fun testMultipleMatchesReturnedSorted() {
        ApplicationManager.getApplication().runWriteAction {
            // Two classes with the same simple name "AuthResponse" in
            // different packages — both should be returned, sorted.
            myFixture.addFileToProject(
                "com/example/aaa/AuthResponse.java",
                "package com.example.aaa; public class AuthResponse {}"
            )
            myFixture.addFileToProject(
                "com/example/zzz/AuthResponse.java",
                "package com.example.zzz; public class AuthResponse {}"
            )
        }
        val result = runBlocking {
            FindClassesByNameTool().execute(mapOf("name" to "AuthResponse"), ctx())
        }
        Assert.assertTrue(result is ToolResult.Text)
        val fqns: List<String> = GsonUtils.fromJson((result as ToolResult.Text).value)
        Assert.assertEquals("should find both matches", 2, fqns.size)
        // Alphabetically sorted: aaa before zzz.
        Assert.assertEquals("com.example.aaa.AuthResponse", fqns[0])
        Assert.assertEquals("com.example.zzz.AuthResponse", fqns[1])
    }

    fun testContextPrefersImportReachableMatch() {
        ApplicationManager.getApplication().runWriteAction {
            // Two classes with the same simple name "AuthResponse".
            // com.example.aaa is alphabetically first; com.example.zzz is
            // last. The context file imports com.example.zzz.AuthResponse,
            // so the zzz variant should be moved to the front.
            myFixture.addFileToProject(
                "com/example/aaa/AuthResponse.java",
                "package com.example.aaa; public class AuthResponse {}"
            )
            myFixture.addFileToProject(
                "com/example/zzz/AuthResponse.java",
                "package com.example.zzz; public class AuthResponse {}"
            )
            // Context file that imports the zzz variant.
            myFixture.addFileToProject(
                "com/example/Order.java",
                """
                package com.example;
                import com.example.zzz.AuthResponse;
                public class Order {
                    public AuthResponse auth;
                }
                """.trimIndent()
            )
        }
        val result = runBlocking {
            FindClassesByNameTool().execute(
                mapOf("name" to "AuthResponse", "context" to "com.example.Order"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val fqns: List<String> = GsonUtils.fromJson((result as ToolResult.Text).value)
        Assert.assertEquals("should find both matches", 2, fqns.size)
        // The context-preferred (zzz) variant should be moved to the front,
        // even though aaa is alphabetically first.
        Assert.assertEquals(
            "context-reachable match should be first",
            "com.example.zzz.AuthResponse",
            fqns[0]
        )
        Assert.assertEquals("com.example.aaa.AuthResponse", fqns[1])
    }

    // --- parameter validation ---

    fun testRejectsMissingParameter() {
        val result = runBlocking {
            FindClassesByNameTool().execute(emptyMap(), ctx())
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("name")
        )
    }

    fun testBlankNameTreatedAsMissing() {
        val result = runBlocking {
            FindClassesByNameTool().execute(mapOf("name" to "   "), ctx())
        }
        Assert.assertTrue("blank name should be treated as missing", result is ToolResult.Error)
    }

    // --- registration + kind + schema ---

    fun testIsRegisteredInStandardToolSet() {
        val names = standardRuleTools().map { it.name }
        Assert.assertTrue(
            "find_classes_by_name must be registered",
            names.contains("find_classes_by_name")
        )
    }

    fun testIsPerceptionTool() {
        Assert.assertEquals(ToolKind.PERCEPTION, FindClassesByNameTool().kind)
    }

    fun testSchemaDeclaresNameAndNamesProperties() {
        val schema = FindClassesByNameTool().parametersSchema
        val props = schema["properties"] as Map<*, *>
        Assert.assertTrue("should declare name", props.containsKey("name"))
        Assert.assertTrue("should declare names", props.containsKey("names"))
        Assert.assertTrue("should declare context", props.containsKey("context"))
    }

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}
