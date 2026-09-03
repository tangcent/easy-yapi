package com.itangcent.easyapi.core.ai.agent

import org.junit.Assert
import org.junit.Test

/**
 * Tests for [SystemPromptBuilder].
 *
 * Covers three concerns (design C2 / task A3):
 * - The base prompt (`build()`) carries identity, loop contract, tool index,
 *   rule-file format, and writing-quality rules — but NOT the detection/recipe
 *   prose (which moved to `ai/detection/` and `ai/key-guides/` catalog files).
 * - The entry-path overload (`build(entryPath, amb)`) composes the right seed
 *   messages per path: 3 for REACTIVE (base + detection index + rule index),
 *   1 for both Task-List variants.
 * - The indexes are enablement-aware: a `channel: postman` rule file is
 *   absent from the rule index when Postman is disabled (AC-S7).
 */
class SystemPromptBuilderTest {

    @Test
    fun `base prompt mentions custom-pattern detection concept`() {
        // The base prompt keeps a one-liner pointing to the detection catalog;
        // the full detection prose lives in ai/detection/*.md (covered by
        // PromptCatalogTest).
        val msg = SystemPromptBuilder.build()
        val text = msg.content
        Assert.assertTrue(
            "base prompt should mention custom framework patterns",
            text.contains("custom framework patterns", ignoreCase = true)
        )
    }

    @Test
    fun `base prompt mentions detection signals summary`() {
        // The base prompt keeps a one-liner enumerating the detection families
        // so the agent knows what to look for; full recipes are fetched via
        // get_detection_prompt.
        val msg = SystemPromptBuilder.build()
        val text = msg.content
        Assert.assertTrue(
            "base prompt should mention Filter / Interceptor / WebFilter",
            text.contains("Filter") && text.contains("WebFilter")
        )
        Assert.assertTrue(
            "base prompt should mention ResponseBodyAdvice",
            text.contains("ResponseBodyAdvice")
        )
        Assert.assertTrue(
            "base prompt should mention HandlerMethodArgumentResolver",
            text.contains("HandlerMethodArgumentResolver")
        )
    }

    @Test
    fun `base prompt teaches both discovery tools`() {
        // The agent must know BOTH declaration styles, otherwise it produces
        // false negatives like "no Filters found" for the standard Spring Boot
        // inheritance style (`extends OncePerRequestFilter`, no `@WebFilter`).
        val msg = SystemPromptBuilder.build()
        val text = msg.content
        Assert.assertTrue(
            "base prompt should mention find_classes_by_annotation",
            text.contains("find_classes_by_annotation")
        )
        Assert.assertTrue(
            "base prompt should mention find_classes_by_supertype",
            text.contains("find_classes_by_supertype")
        )
        Assert.assertTrue(
            "base prompt should call out the inheritance style (OncePerRequestFilter)",
            text.contains("OncePerRequestFilter")
        )
    }

    @Test
    fun `base prompt references knowledge-base doc names not architecture`() {
        val msg = SystemPromptBuilder.build()
        val text = msg.content
        Assert.assertTrue(
            "base prompt should reference rule-guide",
            text.contains("rule-guide")
        )
        Assert.assertFalse(
            "base prompt should NOT reference the removed architecture doc",
            text.contains("architecture")
        )
    }

    @Test
    fun `base prompt mentions multi-app namespacing`() {
        // Per-app env-var namespacing: the base prompt must teach the agent to
        // resolve a namespace key and namespace every env var in a workflow
        // bundle by that key. The full recipe lives in rule-guide.md; the
        // base prompt carries only the condensed detection + on-demand-fetch
        // pointer.
        val msg = SystemPromptBuilder.build()
        val text = msg.content
        Assert.assertTrue(
            "base prompt should mention the Multi-app namespacing subsection",
            text.contains("Multi-app namespacing")
        )
        Assert.assertTrue(
            "base prompt should mention the namespace-key resolution order (module name)",
            text.contains("module name", ignoreCase = true)
        )
        Assert.assertTrue(
            "base prompt should mention the namespace-key resolution order (spring.application.name)",
            text.contains("spring.application.name")
        )
        Assert.assertTrue(
            "base prompt should mention the namespace-key resolution order (ask_clarification)",
            text.contains("ask_clarification")
        )
        Assert.assertTrue(
            "base prompt should name get_module_dependency_graph tool",
            text.contains("get_module_dependency_graph")
        )
    }

    @Test
    fun `base prompt documents catalog detail tools`() {
        // The base prompt's tool index must document the new catalog tools
        // (task A6) so the agent knows when to use which access pattern.
        val msg = SystemPromptBuilder.build()
        val text = msg.content
        Assert.assertTrue(
            "base prompt should document get_detection_prompt",
            text.contains("get_detection_prompt")
        )
        Assert.assertTrue(
            "base prompt should document get_rule_context",
            text.contains("get_rule_context")
        )
        Assert.assertTrue(
            "base prompt should require context lookup before scripts",
            text.contains("writing a Groovy script")
        )
        Assert.assertTrue(
            "base prompt should document get_rule_detail",
            text.contains("get_rule_detail")
        )
        Assert.assertTrue(
            "base prompt should document get_rule_detail's by-key access pattern",
            text.contains("key=")
        )
        Assert.assertTrue(
            "base prompt should document get_rule_detail's by-scope access pattern",
            text.contains("channel=")
        )
    }

    @Test
    fun `base prompt notes planning tools are task-list-only`() {
        // The base prompt must note that create_task_list / update_task exist
        // but are only for Task-List tasks (task A6) — plain chat should not
        // use them.
        val msg = SystemPromptBuilder.build()
        val text = msg.content
        Assert.assertTrue(
            "base prompt should mention create_task_list",
            text.contains("create_task_list")
        )
        Assert.assertTrue(
            "base prompt should mention update_task",
            text.contains("update_task")
        )
        Assert.assertTrue(
            "base prompt should mark planning tools as Task-List-only",
            text.contains("Task-List", ignoreCase = true)
        )
    }

    // ── build(entryPath, amb) — message counts per path (A3) ──

    @Test
    fun `build reactive returns four messages`() {
        // REACTIVE → base + detection index + rule index + L0 rule-key menu.
        val amb = Ambient(
            projectName = "demo",
            editingRuleFile = null,
            existingRuleFiles = emptyList(),
            enabledChannels = listOf("postman"),
            enabledFormats = listOf("json")
        )
        val msgs = SystemPromptBuilder.build(EntryPath.REACTIVE, amb)
        Assert.assertEquals("REACTIVE should return 4 seed messages", 4, msgs.size)
    }

    @Test
    fun `build task list magic returns one message`() {
        val amb = Ambient(
            projectName = "demo",
            editingRuleFile = null,
            existingRuleFiles = emptyList()
        )
        val msgs = SystemPromptBuilder.build(EntryPath.TASK_LIST_MAGIC, amb)
        Assert.assertEquals("TASK_LIST_MAGIC should return 1 seed message", 1, msgs.size)
    }

    @Test
    fun `build task list programmatic returns one message`() {
        val amb = Ambient(
            projectName = "demo",
            editingRuleFile = null,
            existingRuleFiles = emptyList()
        )
        val msgs = SystemPromptBuilder.build(EntryPath.TASK_LIST_PROGRAMMATIC, amb)
        Assert.assertEquals("TASK_LIST_PROGRAMMATIC should return 1 seed message", 1, msgs.size)
    }

    // ── Sub-agent prompt (EntryPath.SUB_AGENT) ──────────────────────
    //
    // The sub-agent base prompt is a separate resource (`sub-agent-base.md`)
    // that advertises ONLY the tools in `subAgentToolRegistry()` (perception +
    // report_findings). It must NOT advertise the orchestrator/Reactive tools
    // the sub-agent cannot call — otherwise the LLM trusts the prompt's tool
    // index over its 6-entry tools schema and calls tools that aren't in its
    // registry, surfacing as "Unknown tool: <name>" (the bug this section
    // pins). See RunSubAgentTool + subAgentToolRegistry for the tool set.

    @Test
    fun `build sub agent returns one message`() {
        val amb = Ambient(
            projectName = "demo",
            editingRuleFile = null,
            existingRuleFiles = emptyList()
        )
        val msgs = SystemPromptBuilder.build(EntryPath.SUB_AGENT, amb)
        Assert.assertEquals("SUB_AGENT should return 1 seed message", 1, msgs.size)
    }

    @Test
    fun `sub-agent base prompt is distinct from the orchestrator base prompt`() {
        // Sanity: the sub-agent must not be seeded with the orchestrator's
        // base prompt. If it were, it would inherit the full tool index and
        // the "unknown tool" bug would return.
        val subAgent = SystemPromptBuilder.buildSubAgent().content
        val orchestrator = SystemPromptBuilder.build().content
        Assert.assertNotEquals(
            "sub-agent base prompt must differ from the orchestrator base prompt",
            orchestrator, subAgent
        )
    }

    @Test
    fun `sub-agent base prompt advertises its registered tools`() {
        // The 7 perception + 1 terminal tools in subAgentToolRegistry() — the
        // prompt's tool index is the only menu the sub-agent LLM should trust.
        val text = SystemPromptBuilder.buildSubAgent().content
        for (tool in listOf(
            "list_rule_keys", "get_script_object_api", "get_rule_detail", "get_rule_context", "get_psi_class_info",
            "find_classes_by_annotation", "find_classes_by_supertype",
            "report_findings"
        )) {
            Assert.assertTrue(
                "sub-agent base prompt should advertise '$tool': $text",
                text.contains(tool)
            )
        }
    }

    @Test
    fun `sub-agent base prompt does not advertise orchestrator or reactive tools`() {
        // The orchestrator/Reactive tools the sub-agent cannot call. These are
        // the exact tools the sub-agent called as "Unknown tool" in idea.log
        // before the fix — pinning their absence from the ADVERTISED tool set
        // prevents the regression.
        //
        // We check ADVERTISEMENT entries — bullet lines that introduce a tool
        // by name (`` - `tool_name` — … ``). The prompt is allowed to *name*
        // a forbidden tool inside clarifying notes (e.g. the "do NOT call any
        // other tool" warning, or "you do not have propose_rule_content — that
        // belongs to the orchestrator"), because those are corrective nudges,
        // not advertisements. What matters is that no forbidden tool is
        // presented as available.
        val full = SystemPromptBuilder.buildSubAgent().content
        // An advertisement is a line whose bullet payload starts with the tool
        // name wrapped in backticks, e.g. "- `get_plugin_doc` — ...".
        val advertisedToolNames = Regex("""(?m)^-\s*`([a-z_]+)`""")
            .findAll(full)
            .map { it.groupValues[1] }
            .toSet()
        for (forbidden in listOf(
            "list_project_endpoints", "get_plugin_doc", "find_classes_by_name",
            "get_existing_rules_for_key", "propose_rule_content",
            "create_task_list", "update_task", "read_rule_file",
            "ask_clarification", "get_detection_prompt", "get_psi_method_info",
            "get_module_dependency_graph", "run_sub_agent"
        )) {
            Assert.assertFalse(
                "sub-agent prompt must NOT advertise orchestrator/Reactive " +
                    "tool '$forbidden' as an available tool " +
                    "(advertised: $advertisedToolNames)",
                forbidden in advertisedToolNames
            )
        }
        // And the registered tools MUST be advertised.
        for (registered in listOf(
            "list_rule_keys", "get_script_object_api", "get_rule_detail", "get_rule_context",
            "get_psi_class_info", "find_classes_by_annotation", "find_classes_by_supertype",
            "report_findings"
        )) {
            Assert.assertTrue(
                "sub-agent prompt MUST advertise registered tool '$registered' " +
                    "(advertised: $advertisedToolNames)",
                registered in advertisedToolNames
            )
        }
    }

    @Test
    fun `sub-agent base prompt forbids unregistered tools`() {
        // The prompt must explicitly tell the model not to call tools outside
        // its registry, so a model that pattern-matches from training data
        // still steers back to the 6 registered tools.
        val text = SystemPromptBuilder.buildSubAgent().content
        Assert.assertTrue(
            "sub-agent base prompt should warn against calling unregistered tools: $text",
            text.contains("do NOT call any other tool", ignoreCase = true) ||
                text.contains("do not call any other tool", ignoreCase = true)
        )
    }

    @Test
    fun `sub-agent base prompt directs toward report_findings as the terminal action`() {
        // report_findings is the sub-agent's ONLY state-changing action and
        // its terminal action. The prompt must teach this so the sub-agent
        // ends its turn correctly instead of looking for propose_rule_content.
        val text = SystemPromptBuilder.buildSubAgent().content
        Assert.assertTrue(
            "sub-agent base prompt should name report_findings as the terminal action: $text",
            text.contains("report_findings")
        )
        Assert.assertTrue(
            "sub-agent base prompt should note report_findings ends the turn: $text",
            text.contains("ends your turn", ignoreCase = true) ||
                text.contains("end your turn", ignoreCase = true) ||
                text.contains("terminal", ignoreCase = true)
        )
    }

    @Test
    fun `build reactive index messages are non-empty when features enabled`() {
        // With postman enabled, the rule index should include the
        // `postman.test` catalog entry (AC-S7).
        val amb = Ambient(
            projectName = "demo",
            editingRuleFile = null,
            existingRuleFiles = emptyList(),
            enabledChannels = listOf("postman")
        )
        val msgs = SystemPromptBuilder.build(EntryPath.REACTIVE, amb)
        // msgs[1] = detection index, msgs[2] = rule index.
        val ruleIndex = msgs[2].content
        Assert.assertTrue(
            "rule index should mention postman.test when Postman is enabled: $ruleIndex",
            ruleIndex.contains("postman.test")
        )
    }

    @Test
    fun `build reactive rule index omits postman entries when postman disabled`() {
        // AC-S7: a `channel: postman` rule file is absent from the rule index
        // when Postman is disabled.
        val amb = Ambient(
            projectName = "demo",
            editingRuleFile = null,
            existingRuleFiles = emptyList(),
            enabledChannels = emptyList() // Postman disabled
        )
        val msgs = SystemPromptBuilder.build(EntryPath.REACTIVE, amb)
        val ruleIndex = msgs[2].content
        Assert.assertFalse(
            "rule index should NOT mention postman.test when Postman is disabled: $ruleIndex",
            ruleIndex.contains("postman.test")
        )
    }

    @Test
    fun `build reactive disabled features keep unscoped entries and drop scoped ones`() {
        // When all features are disabled, scoped entries (e.g. postman.test)
        // are filtered out, but unscoped entries (e.g. field.ignore) still
        // appear — their CatalogScope has all-null fields and always matches
        // (design C1, mirrors PromptCatalogTest.listFor_unscopedEntriesAlwaysAppear).
        // The "(none for the currently enabled features)" body only renders
        // when the catalog is truly empty (e.g. missing manifest).
        val amb = Ambient(
            projectName = "demo",
            editingRuleFile = null,
            existingRuleFiles = emptyList(),
            enabledChannels = emptyList(),
            enabledFormats = emptyList(),
            frameworkHints = emptyList()
        )
        val msgs = SystemPromptBuilder.build(EntryPath.REACTIVE, amb)
        val ruleIndex = msgs[2].content
        // Unscoped entries always appear (json.additional.field has no channel/format/framework).
        Assert.assertTrue(
            "rule index should include unscoped entry json.additional.field when all features are disabled: $ruleIndex",
            ruleIndex.contains("json.additional.field")
        )
        // Scoped entries (channel: postman) are filtered out when their channel is disabled.
        Assert.assertFalse(
            "rule index should NOT include scoped entry postman.test when postman is disabled: $ruleIndex",
            ruleIndex.contains("postman.test")
        )
    }

    @Test
    fun `ambient message includes project name editing file and existing files`() {
        val msg = SystemPromptBuilder.ambient(
            Ambient(
                projectName = "demo",
                editingRuleFile = "my-team.rules",
                existingRuleFiles = listOf("built-in.rules", "security.rules")
            )
        )
        val text = msg.content
        Assert.assertTrue("project name", text.contains("demo"))
        Assert.assertTrue("editing file", text.contains("my-team.rules"))
        Assert.assertTrue("existing files", text.contains("built-in.rules"))
        Assert.assertTrue("existing files", text.contains("security.rules"))
    }

    // ── Ambient user-language hint  ─────────────────────────────────

    @Test
    fun `ambient message includes user language hint when userLanguage is non-null`() {
        val msg = SystemPromptBuilder.ambient(
            Ambient(
                projectName = "demo",
                editingRuleFile = null,
                existingRuleFiles = emptyList(),
                userLanguage = "zh-CN"
            )
        )
        val text = msg.content
        Assert.assertTrue(
            "ambient should include 'user language: zh-CN' hint when userLanguage='zh-CN': $text",
            text.contains("user language: zh-CN", ignoreCase = true)
        )
    }

    @Test
    fun `ambient message omits user language hint when userLanguage is null`() {
        val msg = SystemPromptBuilder.ambient(
            Ambient(
                projectName = "demo",
                editingRuleFile = null,
                existingRuleFiles = emptyList(),
                userLanguage = null
            )
        )
        val text = msg.content
        Assert.assertFalse(
            "ambient should NOT include user-language hint when userLanguage is null: $text",
            text.contains("user language", ignoreCase = true)
        )
    }

    @Test
    fun `ambient message omits user language hint when userLanguage is en`() {
        // 'en' means "use the default (English) template" — no hint should be surfaced
        // (matches AmbientPerception.capture, which returns null for 'en' rules; this test
        // pins the contract defensively in case an Ambient is constructed directly with 'en').
        val msg = SystemPromptBuilder.ambient(
            Ambient(
                projectName = "demo",
                editingRuleFile = null,
                existingRuleFiles = emptyList(),
                userLanguage = "en"
            )
        )
        val text = msg.content
        Assert.assertFalse(
            "ambient should NOT include user-language hint when userLanguage='en': $text",
            text.contains("user language", ignoreCase = true)
        )
    }

    @Test
    fun `ambient message includes user language hint for non-en locale`() {
        // Any non-'en', non-null BCP-47 tag should surface the hint (not just zh-CN).
        val msg = SystemPromptBuilder.ambient(
            Ambient(
                projectName = "demo",
                editingRuleFile = null,
                existingRuleFiles = emptyList(),
                userLanguage = "ja"
            )
        )
        val text = msg.content
        Assert.assertTrue(
            "ambient should include 'user language: ja' hint for non-en locale: $text",
            text.contains("user language: ja", ignoreCase = true)
        )
    }

    // ── Ambient module-names hint  ─────────────────────────────────

    @Test
    fun `ambient message renders module names when moduleNames is non-empty`() {
        val msg = SystemPromptBuilder.ambient(
            Ambient(
                projectName = "demo",
                editingRuleFile = null,
                existingRuleFiles = emptyList(),
                moduleNames = listOf("order-service", "payment-service")
            )
        )
        val text = msg.content
        Assert.assertTrue(
            "ambient should include 'modules: order-service, payment-service' when moduleNames is non-empty: $text",
            text.contains("modules: order-service, payment-service")
        )
    }

    @Test
    fun `ambient message omits module names segment when moduleNames is empty`() {
        // Mirrors the existing userLanguage null-skip pattern — don't surface
        // empty signals (a workspace with no API-bearing modules yields the
        // default emptyList(), which carries no useful perception).
        val msg = SystemPromptBuilder.ambient(
            Ambient(
                projectName = "demo",
                editingRuleFile = null,
                existingRuleFiles = emptyList(),
                moduleNames = emptyList()
            )
        )
        val text = msg.content
        Assert.assertFalse(
            "ambient should NOT include a 'modules:' segment when moduleNames is empty: $text",
            text.contains("modules:")
        )
    }

    // ── Ambient framework-hints  ─────────────────────────────────

    @Test
    fun `ambient message renders framework hints when frameworkHints is non-empty`() {
        // The detected web-framework labels are surfaced so the agent knows
        // which frameworks are active without a list_project_endpoints call.
        // Conditional-append style — mirrors moduleNames/userLanguage.
        val msg = SystemPromptBuilder.ambient(
            Ambient(
                projectName = "demo",
                editingRuleFile = null,
                existingRuleFiles = emptyList(),
                frameworkHints = listOf("SpringMVC", "Feign")
            )
        )
        val text = msg.content
        Assert.assertTrue(
            "ambient should include 'frameworks active: SpringMVC, Feign' when frameworkHints is non-empty: $text",
            text.contains("frameworks active: SpringMVC, Feign")
        )
    }

    @Test
    fun `ambient message omits framework segment when frameworkHints is empty`() {
        // Empty list → no hint (a workspace with no recognized frameworks yields
        // the default emptyList(), which carries no useful perception).
        val msg = SystemPromptBuilder.ambient(
            Ambient(
                projectName = "demo",
                editingRuleFile = null,
                existingRuleFiles = emptyList(),
                frameworkHints = emptyList()
            )
        )
        val text = msg.content
        Assert.assertFalse(
            "ambient should NOT include a 'frameworks active:' segment when frameworkHints is empty: $text",
            text.contains("frameworks active:")
        )
    }

    // ── Ambient enabled-channels / enabled-formats hints (A5b) ──

    @Test
    fun `ambient message renders enabled channels when enabledChannels is non-empty`() {
        // The enabled-export-channel ids are surfaced so the agent knows which
        // destinations are turned on (AC-S7). Conditional-append style —
        // mirrors moduleNames/frameworkHints/userLanguage.
        val msg = SystemPromptBuilder.ambient(
            Ambient(
                projectName = "demo",
                editingRuleFile = null,
                existingRuleFiles = emptyList(),
                enabledChannels = listOf("postman", "markdown")
            )
        )
        val text = msg.content
        Assert.assertTrue(
            "ambient should include 'enabled channels: postman, markdown' when enabledChannels is non-empty: $text",
            text.contains("enabled channels: postman, markdown")
        )
    }

    @Test
    fun `ambient message omits enabled channels segment when enabledChannels is empty`() {
        // Empty list → no hint (carries no useful perception).
        val msg = SystemPromptBuilder.ambient(
            Ambient(
                projectName = "demo",
                editingRuleFile = null,
                existingRuleFiles = emptyList(),
                enabledChannels = emptyList()
            )
        )
        val text = msg.content
        Assert.assertFalse(
            "ambient should NOT include an 'enabled channels:' segment when enabledChannels is empty: $text",
            text.contains("enabled channels:")
        )
    }

    @Test
    fun `ambient message renders enabled formats when enabledFormats is non-empty`() {
        val msg = SystemPromptBuilder.ambient(
            Ambient(
                projectName = "demo",
                editingRuleFile = null,
                existingRuleFiles = emptyList(),
                enabledFormats = listOf("json", "yaml")
            )
        )
        val text = msg.content
        Assert.assertTrue(
            "ambient should include 'enabled formats: json, yaml' when enabledFormats is non-empty: $text",
            text.contains("enabled formats: json, yaml")
        )
    }

    @Test
    fun `ambient message omits enabled formats segment when enabledFormats is empty`() {
        val msg = SystemPromptBuilder.ambient(
            Ambient(
                projectName = "demo",
                editingRuleFile = null,
                existingRuleFiles = emptyList(),
                enabledFormats = emptyList()
            )
        )
        val text = msg.content
        Assert.assertFalse(
            "ambient should NOT include an 'enabled formats:' segment when enabledFormats is empty: $text",
            text.contains("enabled formats:")
        )
    }

    @Test
    fun `ambient message surfaces all enabled-feature hints together`() {
        // Verify the four conditional segments coexist in one ambient message
        // (modules + frameworks + channels + formats + user language).
        val msg = SystemPromptBuilder.ambient(
            Ambient(
                projectName = "demo",
                editingRuleFile = null,
                existingRuleFiles = emptyList(),
                moduleNames = listOf("order-service"),
                frameworkHints = listOf("SpringMVC"),
                enabledChannels = listOf("postman"),
                enabledFormats = listOf("json"),
                userLanguage = "zh-CN"
            )
        )
        val text = msg.content
        Assert.assertTrue("modules segment missing: $text", text.contains("modules: order-service"))
        Assert.assertTrue("frameworks segment missing: $text", text.contains("frameworks active: SpringMVC"))
        Assert.assertTrue("enabled channels segment missing: $text", text.contains("enabled channels: postman"))
        Assert.assertTrue("enabled formats segment missing: $text", text.contains("enabled formats: json"))
        Assert.assertTrue("user language segment missing: $text", text.contains("user language: zh-CN"))
    }

    // ── Token-budget tripwire ──

    @Test
    fun `preamble content stays under token-budget ceiling`() {
        // The preamble is the fixed system prompt appended once at conversation start.
        // Targets a ~600-token preamble budget. A condensed
        // "## Workflow-pattern detection" section (~2.8k chars) was added. This tripwire catches
        // unexpected growth beyond the current actual length + a small headroom.
        //
        // Ceiling raised from 16_500 → 17_800 for the condensed "### Multi-app
        // namespacing" subsection added under "## Workflow-pattern detection"
        // (~600-800-char target subsection + on-demand fetch via
        // `get_plugin_doc name="rule-guide"` for the full recipe; ceiling is the new
        // actual ~16.8k + ~1k headroom).
        // Ceiling raised 17_800 → 19_200 for the agent-psi-resolution preamble update:
        // preamble now documents `find_classes_by_name` as the primary simple-name
        // resolver, `typeFqn` chaining, and `detail="full"` opt-in. Actual ~18.2k
        // + ~1k headroom.
        // Ceiling raised 19_200 → 21_600 for the key-guides catalog update: preamble
        // now documents `list_key_guides` and the key-guide access patterns. Actual
        // ~20.6k + ~1k headroom.
        // Ceiling raised 21_600 → 23_200 for the Knowledge State protocol update:
        // the preamble now documents the stateful-tool receipt protocol and the
        // `get_rule_context` objectRefs + `get_script_object_api` split. Actual
        // ~22.0k + ~1.2k headroom.
        val msg = SystemPromptBuilder.build()
        val content = msg.content
        val ceiling = 23_200 // raised for the Knowledge State protocol update: actual ~22.0k + ~1.2k headroom
        Assert.assertTrue(
            "Preamble content length (${content.length} chars) must stay under $ceiling chars " +
                "to stay within the token budget. If a future section is added, raise the " +
                "ceiling to the new actual length + headroom.",
            content.length < ceiling
        )
    }
}
