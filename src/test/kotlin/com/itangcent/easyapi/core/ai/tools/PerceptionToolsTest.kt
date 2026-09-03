package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.AiRuntimeConfig
import com.itangcent.easyapi.core.ai.agent.AgentEvent
import com.itangcent.easyapi.core.ai.agent.AgentMemory
import com.itangcent.easyapi.core.ai.agent.KnowledgeState
import com.itangcent.easyapi.core.ai.agent.ApprovalGate
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.SourceValue
import com.itangcent.easyapi.core.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.flow.MutableSharedFlow
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
        readConsents = readConsents,
        events = MutableSharedFlow(extraBufferCapacity = 64)
    )

    // --- ListRuleKeysTool ---

    fun testListRuleKeysReturnsKnownKeys() {
        val entries = stateEntries(runBlocking { ListRuleKeysTool().execute(emptyMap(), ctx()) })
        Assert.assertTrue("should contain api.name", entries.containsKey("api.name"))
        Assert.assertTrue("should contain field.ignore", entries.containsKey("field.ignore"))
        Assert.assertTrue("should contain postman.test", entries.containsKey("postman.test"))
    }

    fun testListRuleKeysRendersCompactStateEntries() {
        // Every key's directory line is self-describing: name | source |
        // summary | outputShape | [contexts]. It goes to §keys, never into the
        // transcript as a full JSON blob.
        val stateful = stateful(runBlocking { ListRuleKeysTool().execute(emptyMap(), ctx()) })
        Assert.assertEquals("§keys", stateful.section)
        val line = stateful.entries.first { it.id == "field.ignore" }.renderedLine
        Assert.assertTrue("line should lead with the key name: $line", line.startsWith("field.ignore | "))
        Assert.assertTrue("line should carry the source: $line", line.contains("general"))
        Assert.assertTrue("receipt should report the catalog size", stateful.receiptNote.contains("keys catalogued"))
    }

    fun testListRuleKeysIncludesKeysWithRecipeFile() {
        // `json.additional.field` has ai/key-guides/json.additional.field.md —
        // it is still listed like any other key (guide discovery is served by
        // the enablement-filtered L0 index, not by the directory line).
        val entries = stateEntries(runBlocking { ListRuleKeysTool().execute(emptyMap(), ctx()) })
        Assert.assertTrue(
            "json.additional.field should be in the list",
            entries.containsKey("json.additional.field")
        )
        Assert.assertTrue(
            "json.additional.field should carry a self-describing summary",
            entries["json.additional.field"]!!.split(" | ").size >= 3
        )
    }

    fun testListRuleKeysIncludesKeysWithoutRecipeFile() {
        // Keys without a per-key recipe file (e.g. `api.name`) are still
        // self-describing — they carry `summary` from their scheme.
        val entries = stateEntries(runBlocking { ListRuleKeysTool().execute(emptyMap(), ctx()) })
        Assert.assertTrue("api.name should be in the list", entries.containsKey("api.name"))
    }

    fun testListRuleKeysDoesNotThrowOnMissingCatalog() {
        val result = runBlocking { ListRuleKeysTool().execute(emptyMap(), ctx()) }
        Assert.assertTrue("result: $result", result is ToolResult.Stateful)
        val entries = stateEntries(result)
        Assert.assertTrue("should contain api.name", entries.containsKey("api.name"))
        Assert.assertTrue("every line should carry a source", entries.values.all { it.contains(" | ") })
    }

    // --- ListRuleKeysTool (continued) ---

    fun testListRuleKeysIncludesImplicitKey() {
        // markdown.curl.host is read by name via ConfigReader.getFirst(...) and
        // registered as an implicit key — so list_rule_keys surfaces it too.
        val entries = stateEntries(runBlocking { ListRuleKeysTool().execute(emptyMap(), ctx()) })
        val curlHost = entries["markdown.curl.host"]
        Assert.assertNotNull("markdown.curl.host should be in list_rule_keys", curlHost)
        Assert.assertTrue(
            "markdown.curl.host should be tagged implicit: $curlHost",
            curlHost!!.startsWith("markdown.curl.host | implicit | ")
        )
    }

    // --- GetRuleContextTool ---

    fun testGetRuleContextReturnsStatefulKeyContext() {
        val stateful = stateful(runBlocking {
            GetRuleContextTool().execute(mapOf("key" to "http.call.after"), ctx())
        })
        Assert.assertEquals("§keyContexts", stateful.section)
        val entry = stateful.entries.single()
        Assert.assertEquals("http.call.after", entry.id)
        Assert.assertTrue("line: ${entry.renderedLine}", entry.renderedLine.startsWith("http.call.after | "))
        Assert.assertTrue(
            "receipt should point at get_script_object_api: ${stateful.receiptNote}",
            stateful.receiptNote.contains("get_script_object_api")
        )
    }

    fun testGetRuleContextDoesNotWriteObjectsSection() {
        // §objects has exactly one writer (get_script_object_api). If
        // get_rule_context also wrote it, its method-count-only rendering
        // would overwrite the full signatures.
        val memory = AgentMemory()
        runBlocking { GetRuleContextTool().execute(mapOf("key" to "http.call.after"), ctx(workingMemory = memory)) }
        Assert.assertTrue(
            "get_rule_context must not touch §objects",
            memory.knowledgeState.entries("§objects").isEmpty()
        )
    }

    fun testGetScriptObjectApiWritesFullSignaturesIntoObjectsSection() {
        val stateful = stateful(runBlocking {
            GetScriptObjectApiTool().execute(mapOf("ids" to listOf("logger")), ctx())
        })
        Assert.assertEquals("§objects", stateful.section)
        val line = stateful.entries.single().renderedLine
        Assert.assertTrue("line should start with the object id: $line", line.startsWith("logger | "))
        Assert.assertTrue(
            "line should carry method signatures, not just a count: $line",
            line.contains("(")
        )
    }

    fun testGetRuleContextResolvesAliasToCanonicalKey() {
        // An alias must be filed under the canonical key, otherwise a later
        // call with the canonical name appends a second, identical line.
        val aliasResult = stateful(runBlocking {
            GetRuleContextTool().execute(mapOf("key" to "doc.field"), ctx())
        })
        val entry = aliasResult.entries.single()
        Assert.assertEquals("field.doc", entry.id)
        Assert.assertTrue("line: ${entry.renderedLine}", entry.renderedLine.startsWith("field.doc | "))
        Assert.assertTrue(
            "receipt should tell the model to author the canonical key: ${aliasResult.receiptNote}",
            aliasResult.receiptNote.contains("field.doc") &&
                aliasResult.receiptNote.contains("alias")
        )

        val unknownResult = runBlocking {
            GetRuleContextTool().execute(mapOf("key" to "not.a.real.key"), ctx())
        }
        Assert.assertTrue(unknownResult is ToolResult.Error)
    }

    fun testGetRuleContextAliasThenCanonicalAddsOneEntry() {
        val memory = AgentMemory()
        val alias = stateful(runBlocking {
            GetRuleContextTool().execute(mapOf("key" to "doc.field"), ctx(workingMemory = memory))
        })
        val canonical = stateful(runBlocking {
            GetRuleContextTool().execute(mapOf("key" to "field.doc"), ctx(workingMemory = memory))
        })
        Assert.assertEquals("both calls must target the same id", alias.entries.single().id, canonical.entries.single().id)

        memory.knowledgeState.upsert(alias.section, alias.entries)
        val second = memory.knowledgeState.upsert(canonical.section, canonical.entries)
        Assert.assertTrue("second upsert must be a no-op, was $second", second is KnowledgeState.UpsertResult.NoChange)
        Assert.assertEquals(1, memory.knowledgeState.entries("§keyContexts").size)
    }

    fun testGetRuleContextRejectsMissingKey() {
        val result = runBlocking { GetRuleContextTool().execute(emptyMap(), ctx()) }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue((result as ToolResult.Error).message.contains("missing required parameter"))
    }

    // --- helpers ---

    private fun stateful(result: ToolResult): ToolResult.Stateful =
        result as? ToolResult.Stateful
            ?: error("expected a Stateful tool result, got ${result::class.simpleName}: $result")

    /** id → renderedLine for a [ToolResult.Stateful] catalog/context result. */
    private fun stateEntries(result: ToolResult): Map<String, String> =
        stateful(result).entries.associate { it.id to it.renderedLine }

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
        // The postman-script-reference doc IS wired — the tool
        // should return its content.
        val result = runBlocking {
            GetPluginDocTool().execute(mapOf("name" to "postman-script-reference"), ctx())
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

    fun testReadRuleFileStripsBom() {
        // Issue #755: rule files written by the plugin carry a UTF-8 BOM so
        // IDEA detects the encoding; read_rule_file must strip it so the
        // agent sees clean content.
        val basePath = project.basePath ?: throw IllegalStateException("project base path required")
        val easyapiDir = java.io.File(basePath, ".easyapi").apply { mkdirs() }
        try {
            val bom = String(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), Charsets.UTF_8)
            java.io.File(easyapiDir, "bombed.properties").writeText(bom + "api.name=BomFree")

            val result = runBlocking {
                ReadRuleFileTool().execute(mapOf("path" to "bombed.properties"), ctx())
            }
            Assert.assertTrue("result: $result", result is ToolResult.Text)
            Assert.assertEquals("api.name=BomFree", (result as ToolResult.Text).value)
        } finally {
            easyapiDir.deleteRecursively()
        }
    }

    fun testReadRuleFileRelativePathResolvesAgainstProjectDir() {
        // Issue #754: a consented relative path must resolve against the
        // project base directory, not the process working directory. The
        // project root is not a tracked rule dir here, so the read goes
        // through the consent gate; once granted, the project-root file
        // is read.
        val basePath = project.basePath ?: throw IllegalStateException("project base path required")
        val rootRuleFile = java.io.File(basePath, "root-rule-754.properties")
        try {
            rootRuleFile.writeText("api.name=ProjectRoot")
            val gate = com.itangcent.easyapi.core.ai.agent.FakeFileReadConsentGate(grant = true)
            val result = runBlocking {
                ReadRuleFileTool().execute(mapOf("path" to "root-rule-754.properties"), ctx(readConsents = gate))
            }
            Assert.assertTrue("result: $result", result is ToolResult.Text)
            Assert.assertEquals("api.name=ProjectRoot", (result as ToolResult.Text).value)
        } finally {
            rootRuleFile.delete()
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
        Assert.assertTrue("get_rule_context", names.contains("get_rule_context"))
        Assert.assertTrue("get_script_object_api", names.contains("get_script_object_api"))
        // D2.3: list_key_guides was folded into list_rule_keys and removed.
        Assert.assertFalse("list_key_guides must be gone", names.contains("list_key_guides"))
        Assert.assertTrue("get_plugin_doc", names.contains("get_plugin_doc"))
        Assert.assertTrue("get_detection_prompt", names.contains("get_detection_prompt"))
        Assert.assertTrue("get_rule_detail", names.contains("get_rule_detail"))
        Assert.assertTrue("read_rule_file", names.contains("read_rule_file"))
        Assert.assertTrue("list_project_endpoints", names.contains("list_project_endpoints"))
        Assert.assertTrue("get_psi_class_info", names.contains("get_psi_class_info"))
        Assert.assertTrue("get_psi_method_info", names.contains("get_psi_method_info"))
        Assert.assertTrue("find_classes_by_annotation", names.contains("find_classes_by_annotation"))
        Assert.assertTrue("find_classes_by_supertype", names.contains("find_classes_by_supertype"))
        Assert.assertTrue("find_classes_by_name", names.contains("find_classes_by_name"))
        Assert.assertTrue("get_existing_rules_for_key", names.contains("get_existing_rules_for_key"))
        Assert.assertTrue("get_module_dependency_graph", names.contains("get_module_dependency_graph"))
        Assert.assertTrue("ask_clarification", names.contains("ask_clarification"))
        Assert.assertTrue("propose_rule_content", names.contains("propose_rule_content"))
        // Phase B — Task-List-path planning tools (design C7).
        Assert.assertTrue("create_task_list", names.contains("create_task_list"))
        Assert.assertTrue("update_task", names.contains("update_task"))
        Assert.assertFalse(
            "write_rule_file must NOT be registered in v1",
            names.contains("write_rule_file")
        )
        Assert.assertEquals("exactly 19 tools (17 + 2 task-list tools)", 19, tools.size)
    }

    // --- GetDetectionPromptTool ---

    fun testGetDetectionPromptReturnsBodyForValidId() {
        // "static-auth" is one of the seeded detection catalog files.
        val result = runBlocking {
            GetDetectionPromptTool().execute(mapOf("id" to "static-auth"), ctx())
        }
        Assert.assertTrue("result: $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("body should be non-empty", text.isNotBlank())
    }

    fun testGetDetectionPromptReturnsErrorForUnknownId() {
        val result = runBlocking {
            GetDetectionPromptTool().execute(mapOf("id" to "does-not-exist"), ctx())
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue((result as ToolResult.Error).message.contains("unknown detection id"))
    }

    fun testGetDetectionPromptReturnsErrorForMissingId() {
        val result = runBlocking {
            GetDetectionPromptTool().execute(emptyMap(), ctx())
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue((result as ToolResult.Error).message.contains("missing required parameter"))
    }

    // --- GetRuleDetailTool ---

    fun testGetRuleDetailByKeyReturnsBody() {
        // By-key lookup: `key=postman.test` returns the single per-key recipe.
        val result = runBlocking {
            GetRuleDetailTool().execute(mapOf("key" to "postman.test"), ctx())
        }
        Assert.assertTrue("result: $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("body should be non-empty", text.isNotBlank())
        Assert.assertTrue(
            "body should mention postman.test recipe content",
            text.contains("pm.response") || text.contains("postman.test")
        )
    }

    fun testGetRuleDetailByKeyReturnsErrorForUnknownKey() {
        val result = runBlocking {
            GetRuleDetailTool().execute(mapOf("key" to "not.a.real.key"), ctx())
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue((result as ToolResult.Error).message.contains("unknown rule key"))
    }

    fun testGetRuleDetailByKeyOverridesScope() {
        // When both `key` and `channel` are supplied, `key` wins.
        val result = runBlocking {
            GetRuleDetailTool().execute(
                mapOf("key" to "method.additional.header", "channel" to "postman"),
                ctx()
            )
        }
        Assert.assertTrue("result: $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        // method.additional.header guide content — not a postman-scoped guide.
        Assert.assertTrue(
            "should return method.additional.header guide (key wins over scope)",
            text.contains("method.additional.header") || text.contains("JSON object")
        )
    }

    fun testGetRuleDetailByKeyFallsBackToSchemeProfile() {
        // A registered key with no key-guide file (e.g. field.ignore) falls
        // back to its self-describing scheme profile instead of Error.
        val result = runBlocking {
            GetRuleDetailTool().execute(mapOf("key" to "field.ignore"), ctx())
        }
        Assert.assertTrue("result: $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue(
            "scheme fallback should name the key",
            text.contains("field.ignore")
        )
        Assert.assertTrue(
            "scheme fallback should carry the self-describing description",
            text.contains("Source") || text.contains("Execution mode")
        )
        // The no-guide fallback must stay a *reference*, not a full dump:
        // full method signatures belong to get_script_object_api (§objects).
        // Guard against regressions that re-inline tens of KB per key.
        Assert.assertTrue(
            "scheme fallback should point at get_script_object_api for method signatures",
            text.contains("get_script_object_api") || !text.contains("Object APIs:")
        )
        Assert.assertTrue(
            "scheme fallback must stay compact (was ${text.length} chars)",
            text.length < 8_000
        )
    }

    fun testGetRuleDetailNoArgsReturnsError() {
        val result = runBlocking {
            GetRuleDetailTool().execute(emptyMap(), ctx())
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("provide at least one")
        )
    }

    fun testGetRuleDetailScopeQueryReturnsEntriesWhenChannelEnabled() {
        // Scope query: `channel=postman` returns ≥1 file when Postman is
        // enabled (via ambient on working memory).
        val memory = AgentMemory()
        memory.ambient = com.itangcent.easyapi.core.ai.agent.Ambient(
            projectName = "demo",
            editingRuleFile = null,
            existingRuleFiles = emptyList(),
            enabledChannels = listOf("postman")
        )
        val result = runBlocking {
            GetRuleDetailTool().execute(mapOf("channel" to "postman"), ctx(workingMemory = memory))
        }
        Assert.assertTrue("result: $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue(
            "scope query should return postman recipes when enabled",
            text.contains("postman.test") || text.contains("postman.prerequest")
        )
    }

    fun testGetRuleDetailScopeQueryReturnsEmptyTextWhenChannelDisabled() {
        // Scope query with a disabled channel returns the empty-result Text
        // (not Error) so the agent can react gracefully.
        val memory = AgentMemory()
        memory.ambient = com.itangcent.easyapi.core.ai.agent.Ambient(
            projectName = "demo",
            editingRuleFile = null,
            existingRuleFiles = emptyList(),
            enabledChannels = emptyList() // Postman disabled
        )
        val result = runBlocking {
            GetRuleDetailTool().execute(mapOf("channel" to "postman"), ctx(workingMemory = memory))
        }
        Assert.assertTrue("result: $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue(
            "disabled-channel scope query should return the empty-result text",
            text.contains("no rule-detail files match")
        )
    }

    // --- helpers ---

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}
