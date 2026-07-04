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
 * Tests for [GetPsiClassInfoTool].
 *
 * Verifies single-class lookup, batch lookup, the missing-class error path,
 * and the `fqns`-array batch mode. Uses the light fixture's VFS to provide
 * source classes — the tool resolves classes via [com.intellij.psi.JavaPsiFacade.findClass]
 * in the project scope.
 */
class GetPsiClassInfoToolTest : EasyApiLightCodeInsightFixtureTestCase() {

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
                "com/example/User.java",
                """
                package com.example;
                public class User {
                    private String name;
                    private int age;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/Order.java",
                """
                package com.example;
                public class Order {
                    public String id;
                    public int total;
                }
                """.trimIndent()
            )
        }
    }

    fun testReturnsInfoForSingleClass() {
        addClasses()
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqn" to "com.example.User"),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("should contain name", text.contains("User"))
        Assert.assertTrue("should contain fqn", text.contains("com.example.User"))
        // Should list fields and methods.
        Assert.assertTrue("should mention field name", text.contains("name"))
        Assert.assertTrue("should mention method getName", text.contains("getName"))
    }

    fun testReturnsErrorWhenClassNotFound() {
        addClasses()
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqn" to "com.example.DoesNotExist"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("not found")
        )
    }

    fun testRejectsMissingParameter() {
        val result = runBlocking {
            GetPsiClassInfoTool().execute(emptyMap(), ctx())
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("missing parameter")
        )
    }

    fun testBlankFqnTreatedAsMissing() {
        val result = runBlocking {
            GetPsiClassInfoTool().execute(mapOf("fqn" to "   "), ctx())
        }
        Assert.assertTrue(result is ToolResult.Error)
    }

    fun testBatchReturnsMapOfFqnsToInfo() {
        addClasses()
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqns" to listOf("com.example.User", "com.example.Order")),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("should contain User key", text.contains("com.example.User"))
        Assert.assertTrue("should contain Order key", text.contains("com.example.Order"))
    }

    fun testBatchIncludesNotFoundEntryForMissingClass() {
        addClasses()
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqns" to listOf("com.example.User", "com.example.Missing")),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        // The present class returns its info map; the missing one returns
        // the "not found" string placeholder.
        Assert.assertTrue("should contain User info", text.contains("com.example.User"))
        Assert.assertTrue(
            "missing class should map to 'not found'",
            text.contains("not found")
        )
    }

    fun testBatchIgnoresBlankEntries() {
        addClasses()
        // Blank entries in the `fqns` array must be filtered out, leaving
        // a single-element batch that resolves the one non-blank FQN.
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqns" to listOf("com.example.User", "  ", "")),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("should contain User info", text.contains("com.example.User"))
    }

    fun testSingleFqnsArrayWithOneElementReturnsObjectMap() {
        // When `fqns` has exactly one element, the tool still returns the
        // batch shape (a JSON object mapping FQN → info), not the single
        // object shape. This verifies the branch that picks batch vs single
        // based on list size.
        addClasses()
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqns" to listOf("com.example.User")),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        // The single-`fqn` path returns a flat object with "name"; the
        // single-element `fqns` path returns { "com.example.User": {...} }.
        val text = (result as ToolResult.Text).value
        Assert.assertTrue(
            "single-element fqns should still return a map keyed by FQN",
            text.contains("\"com.example.User\"")
        )
    }

    fun testIsRegisteredInStandardToolSet() {
        val names = standardRuleTools().map { it.name }
        Assert.assertTrue(
            "get_psi_class_info must be registered",
            names.contains("get_psi_class_info")
        )
    }

    fun testIsPerceptionTool() {
        Assert.assertEquals(ToolKind.PERCEPTION, GetPsiClassInfoTool().kind)
    }

    fun testSchemaDeclaresFqnAndFqnsProperties() {
        val schema = GetPsiClassInfoTool().parametersSchema
        val props = schema["properties"] as Map<*, *>
        Assert.assertTrue("should declare fqn", props.containsKey("fqn"))
        Assert.assertTrue("should declare fqns", props.containsKey("fqns"))
    }

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}
