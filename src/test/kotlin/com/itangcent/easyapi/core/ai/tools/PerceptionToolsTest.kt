package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.AiRuntimeConfig
import com.itangcent.easyapi.core.ai.agent.AgentMemory
import com.itangcent.easyapi.core.ai.agent.ApprovalGate
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.SourceValue
import com.itangcent.easyapi.core.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import java.nio.file.Files

/**
 * Tests for the non-PSI tools in the standard rule-authoring tool set
 *. PSI-touching tools ([GetPsiClassInfoTool],
 * [GetPsiMethodInfoTool], [FindClassesByAnnotationTool]) need a richer fixture
 * with sample source — covered separately.
 */
class PerceptionToolsTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val fakeApprovalGate = NoOpApprovalGate()

    private fun ctx(
        configReader: ConfigReader = ConfigReader.getInstance(project),
        workingMemory: AgentMemory = AgentMemory(),
        readConsents: com.itangcent.easyapi.core.ai.agent.FileReadConsentGate =
            com.itangcent.easyapi.core.ai.agent.FileReadConsentGate.NOOP
    ): ToolContext = ToolContext(
        project = project,
        configReader = configReader,
        aiSettings = AiRuntimeConfig(
            provider = com.itangcent.easyapi.core.ai.AiProvider.OPENAI,
            baseUrl = "", apiKey = "", model = "",
            requestTimeoutSec = 30, maxRequests = 8
        ),
        ruleFileResolver = RuleFileResolver(project),
        workingMemory = workingMemory,
        approvals = fakeApprovalGate,
        readConsents = readConsents
    )

    // --- ListRuleKeysTool ---

    fun testListRuleKeysReturnsKnownKeys() {
        val result = runBlocking { ListRuleKeysTool().execute(emptyMap(), ctx()) }
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("should contain api.name", text.contains("api.name"))
        Assert.assertTrue("should contain field.ignore", text.contains("field.ignore"))
        Assert.assertTrue("should contain postman.test", text.contains("postman.test"))
    }

    // --- GetPluginDocTool ---

    fun testGetPluginDocRejectsUnknownName() {
        val result = runBlocking { GetPluginDocTool().execute(mapOf("name" to "nope"), ctx()) }
        Assert.assertTrue(result is ToolResult.Error)
    }

    fun testGetPluginDocRejectsMissingName() {
        val result = runBlocking { GetPluginDocTool().execute(emptyMap(), ctx()) }
        Assert.assertTrue(result is ToolResult.Error)
    }

    fun testGetPluginDocReturnsRuleGuide() {
        // The rule-guide doc IS wired (Phase 4) — the tool should return its content.
        val result = runBlocking {
            GetPluginDocTool().execute(mapOf("name" to "rule-guide"), ctx())
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("rule guide should be non-empty", text.isNotBlank())
        Assert.assertTrue("rule guide should mention a rule key",
            text.contains("api.name") || text.contains("field.ignore"))
    }

    fun testGetPluginDocReturnsScriptReference() {
        // The easyapi-script-reference doc IS wired (task 2.7) — the tool
        // should return its content.
        val result = runBlocking {
            GetPluginDocTool().execute(mapOf("name" to "easyapi-script-reference"), ctx())
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("script reference should be non-empty", text.isNotBlank())
        Assert.assertTrue("script reference should mention a script binding",
            text.contains("logger") || text.contains("session"))
    }

    // --- ReadRuleFileTool ---

    fun testReadRuleFileRefusesOutsideAllowedDir() {
        val result = runBlocking {
            ReadRuleFileTool().execute(mapOf("path" to "/etc/passwd"), ctx())
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("outside allowed")
        )
    }

    fun testReadRuleFileHintsAtPsiToolForSourceFiles() {
        // When the AI passes a.java path, the error should point it at
        // get_psi_class_info so the next turn converges instead of looping.
        val result = runBlocking {
            ReadRuleFileTool().execute(
                mapOf("path" to "/home/user/project/src/main/java/com/example/Foo.java"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Error)
        val msg = (result as ToolResult.Error).message
        Assert.assertTrue("should mention outside allowed", msg.contains("outside allowed"))
        Assert.assertTrue("should hint at get_psi_class_info", msg.contains("get_psi_class_info"))
    }

    fun testReadRuleFileReadsAllowedFile() {
        // Write a rule file into the project's `.easyapi/` folder — the new
        // folder-based model — and resolve it via the tool.
        val basePath = project.basePath ?: throw IllegalStateException("project base path required")
        val easyapiDir = java.io.File(basePath, ".easyapi").apply { mkdirs() }
        try {
            val ruleFile = java.io.File(easyapiDir, "rule.config").apply { writeText("api.name=Test") }

            val result = runBlocking {
                ReadRuleFileTool().execute(mapOf("path" to ruleFile.absolutePath), ctx())
            }
            Assert.assertTrue(result is ToolResult.Text)
            Assert.assertEquals("api.name=Test", (result as ToolResult.Text).value)
        } finally {
            easyapiDir.deleteRecursively()
        }
    }

    fun testReadRuleFileResolvesByBareName() {
        // Addressing a file by name (not absolute path) should resolve it
        // inside the project.easyapi/ folder.
        val basePath = project.basePath ?: throw IllegalStateException("project base path required")
        val easyapiDir = java.io.File(basePath, ".easyapi").apply { mkdirs() }
        try {
            java.io.File(easyapiDir, "by-name.properties").apply { writeText("api.name=ByName") }

            val result = runBlocking {
                ReadRuleFileTool().execute(mapOf("path" to "by-name.properties"), ctx())
            }
            Assert.assertTrue("result: $result", result is ToolResult.Text)
            Assert.assertEquals("api.name=ByName", (result as ToolResult.Text).value)
        } finally {
            easyapiDir.deleteRecursively()
        }
    }

    fun testReadRuleFileResolvesByProjectScopePrefix() {
        val basePath = project.basePath ?: throw IllegalStateException("project base path required")
        val easyapiDir = java.io.File(basePath, ".easyapi").apply { mkdirs() }
        try {
            java.io.File(easyapiDir, "scoped.rules").apply { writeText("api.name=Scoped") }

            val result = runBlocking {
                ReadRuleFileTool().execute(mapOf("path" to "project:scoped.rules"), ctx())
            }
            Assert.assertTrue("result: $result", result is ToolResult.Text)
            Assert.assertEquals("api.name=Scoped", (result as ToolResult.Text).value)
        } finally {
            easyapiDir.deleteRecursively()
        }
    }

    fun testReadRuleFileSourcePathNeverAsksConsent() {
        // A.java path is refused outright — the consent gate must never be
        // consulted, and the error points at get_psi_class_info.
        val gate = com.itangcent.easyapi.core.ai.agent.FakeFileReadConsentGate(grant = true)
        val result = runBlocking {
            ReadRuleFileTool().execute(
                mapOf("path" to "/home/user/project/src/main/java/com/example/Foo.java"),
                ctx(readConsents = gate)
            )
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertFalse("source path must not trigger consent", gate.wasConsulted)
        Assert.assertTrue((result as ToolResult.Error).message.contains("get_psi_class_info"))
    }

    fun testReadRuleFileOutOfScopeAsksConsentAndReadsWhenApproved() {
        // A non-source, non-system path outside the allow-lists should ask
        // for one-time consent; an approved read returns the file contents.
        val tmp = Files.createTempFile("easyapi-consent-", ".properties").toFile()
        try {
            tmp.writeText("api.name=Consented")
            val gate = com.itangcent.easyapi.core.ai.agent.FakeFileReadConsentGate(grant = true)
            val result = runBlocking {
                ReadRuleFileTool().execute(mapOf("path" to tmp.absolutePath), ctx(readConsents = gate))
            }
            Assert.assertTrue("result: $result", result is ToolResult.Text)
            Assert.assertTrue("consent should have been requested", gate.wasConsulted)
            Assert.assertEquals(tmp.absolutePath, gate.lastRequestedPath)
            Assert.assertEquals("api.name=Consented", (result as ToolResult.Text).value)
        } finally {
            tmp.delete()
        }
    }

    fun testReadRuleFileOutOfScopeDeniedReturnsError() {
        val tmp = Files.createTempFile("easyapi-deny-", ".properties").toFile()
        try {
            tmp.writeText("api.name=Denied")
            val gate = com.itangcent.easyapi.core.ai.agent.FakeFileReadConsentGate(grant = false)
            val result = runBlocking {
                ReadRuleFileTool().execute(mapOf("path" to tmp.absolutePath), ctx(readConsents = gate))
            }
            Assert.assertTrue(result is ToolResult.Error)
            Assert.assertTrue((result as ToolResult.Error).message.contains("outside allowed"))
        } finally {
            tmp.delete()
        }
    }

    // --- GetExistingRulesForKeyTool ---

    fun testGetExistingRulesFallsBackToGetAll() {
        // The fake ConfigReader below doesn't override sourcesForKey, so the
        // M-9 fallback should kick in and return values from getAll.
        val fakeReader = object : ConfigReader {
            override fun getFirst(key: String): String? = "v1"
            override fun getAll(key: String): List<String> = listOf("v1", "v2")
            override suspend fun reload() {}
            override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {
                if (keyFilter("api.name")) action("api.name", "v1")
            }
        }
        val result = runBlocking {
            GetExistingRulesForKeyTool().execute(mapOf("key" to "api.name"), ctx(fakeReader))
        }
        val text = (result as ToolResult.Text).value
        Assert.assertTrue(text.contains("v1"))
        Assert.assertTrue(text.contains("v2"))
    }

    fun testGetExistingRulesUsesSourceMetadataWhenAvailable() {
        val fakeReader = object : ConfigReader {
            override fun getFirst(key: String): String? = null
            override fun getAll(key: String): List<String> = emptyList()
            override suspend fun reload() {}
            override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {}
            override fun sourcesForKey(key: String): List<SourceValue> = listOf(
                SourceValue(sourceId = "project-config", priority = 10, value = "high"),
                SourceValue(sourceId = "global-config", priority = 1, value = "low")
            )
        }
        val result = runBlocking {
            GetExistingRulesForKeyTool().execute(mapOf("key" to "api.name"), ctx(fakeReader))
        }
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("should expose sourceId", text.contains("project-config"))
        Assert.assertTrue("should expose priority", text.contains("10"))
        Assert.assertTrue("should expose value", text.contains("high"))
    }

    fun testGetExistingRulesBatchReturnsMapOfKeysToValues() {
        val fakeReader = object : ConfigReader {
            override fun getFirst(key: String): String? = null
            override fun getAll(key: String): List<String> = listOf("val-$key")
            override suspend fun reload() {}
            override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {}
        }
        val result = runBlocking {
            GetExistingRulesForKeyTool().execute(
                mapOf("keys" to listOf("api.name", "field.ignore")),
                ctx(fakeReader)
            )
        }
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("should contain api.name key", text.contains("api.name"))
        Assert.assertTrue("should contain field.ignore key", text.contains("field.ignore"))
        Assert.assertTrue("should contain val-api.name", text.contains("val-api.name"))
        Assert.assertTrue("should contain val-field.ignore", text.contains("val-field.ignore"))
    }

    fun testGetExistingRulesRejectsMissingBothKeyAndKeys() {
        val result = runBlocking {
            GetExistingRulesForKeyTool().execute(emptyMap(), ctx())
        }
        Assert.assertTrue(result is ToolResult.Error)
    }

    // --- ProposeRuleContentTool ---

    fun testProposeRuleContentStagesProposal() {
        val memory = AgentMemory()
        val result = runBlocking {
            ProposeRuleContentTool().execute(
                mapOf(
                    "content" to "api.name=Test\nfield.ignore=id",
                    "suggestedFileName" to "custom.rules"
                ),
                ctx(workingMemory = memory)
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        Assert.assertNotNull("proposal should be staged", memory.proposal)
        Assert.assertEquals("api.name=Test\nfield.ignore=id", memory.proposal?.content)
        Assert.assertEquals("custom.rules", memory.proposal?.suggestedFileName)
    }

    fun testProposeRuleContentRejectsMissingArgs() {
        val memory = AgentMemory()
        val result = runBlocking {
            ProposeRuleContentTool().execute(emptyMap(), ctx(workingMemory = memory))
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertNull(memory.proposal)
    }

    fun testProposeRuleContentIsNotApprovalGated() {
        // The tool's requiresApproval must be false — staging is harmless.
        Assert.assertFalse(ProposeRuleContentTool().requiresApproval)
    }

    fun testProposeRuleContentRejectsInvalidKey() {
        val memory = AgentMemory()
        val result = runBlocking {
            ProposeRuleContentTool().execute(
                mapOf(
                    "content" to "api.header=X-Foo:bar",
                    "suggestedFileName" to "custom.rules"
                ),
                ctx(workingMemory = memory)
            )
        }
        Assert.assertTrue("result: $result", result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("unknown rule key") ||
                result.message.contains("api.header")
        )
        Assert.assertNull("invalid proposals must not be staged", memory.proposal)
    }

    fun testProposeRuleContentAttachesReviewerNotesForWarnings() {
        val memory = AgentMemory()
        val content = "method.doc[class:com.example.UserController]=user"
        val result = runBlocking {
            ProposeRuleContentTool().execute(
                mapOf("content" to content, "suggestedFileName" to "custom.rules"),
                ctx(workingMemory = memory)
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val staged = memory.proposal
        Assert.assertNotNull(staged)
        Assert.assertTrue(
            "warnings should be prepended as reviewer notes",
            staged!!.content.startsWith("# Reviewer notes:")
        )
        Assert.assertTrue(staged.content.contains("deprecated"))
        // Original content must still be present below the notes.
        Assert.assertTrue(staged.content.contains("method.doc["))
    }

    // --- WriteRuleFileTool (reserved stub) ---

    fun testWriteRuleFileStubRequiresApproval() {
        // Even though it's not registered in v1, the class must declare
        // requiresApproval = true so a future version gets gating for free.
        Assert.assertTrue(WriteRuleFileTool().requiresApproval)
    }

    // --- standardRuleTools() factory ---

    fun testStandardRuleToolsContainsExpectedSet() {
        val tools = standardRuleTools()
        val names = tools.map { it.name }.toSet()
        Assert.assertTrue("list_rule_keys", names.contains("list_rule_keys"))
        Assert.assertTrue("get_plugin_doc", names.contains("get_plugin_doc"))
        Assert.assertTrue("read_rule_file", names.contains("read_rule_file"))
        Assert.assertTrue("list_project_endpoints", names.contains("list_project_endpoints"))
        Assert.assertTrue("get_psi_class_info", names.contains("get_psi_class_info"))
        Assert.assertTrue("get_psi_method_info", names.contains("get_psi_method_info"))
        Assert.assertTrue("find_classes_by_annotation", names.contains("find_classes_by_annotation"))
        Assert.assertTrue("find_classes_by_supertype", names.contains("find_classes_by_supertype"))
        Assert.assertTrue("find_classes_by_name", names.contains("find_classes_by_name"))
        Assert.assertTrue("get_existing_rules_for_key", names.contains("get_existing_rules_for_key"))
        Assert.assertTrue("get_module_dependency_graph", names.contains("get_module_dependency_graph"))
        Assert.assertTrue("propose_rule_content", names.contains("propose_rule_content"))
        Assert.assertFalse(
            "write_rule_file must NOT be registered in v1",
            names.contains("write_rule_file")
        )
        Assert.assertEquals("exactly 13 tools in v1", 13, tools.size)
    }

    // --- helpers ---

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}
