package com.itangcent.easyapi.ai.agent

import org.junit.Assert
import org.junit.Test

/**
 * Tests for [SystemPromptBuilder].
 *
 * Verifies the preamble mentions the Custom-Pattern Catalog and the
 * detection workflow so the agent is primed to scan for custom framework
 * patterns before proposing rules.
 */
class SystemPromptBuilderTest {

    @Test
    fun `preamble mentions custom-pattern detection`() {
        val msg = SystemPromptBuilder.build()
        val text = msg.content
        Assert.assertTrue(
            "preamble should mention custom framework patterns",
            text.contains("custom framework patterns", ignoreCase = true)
        )
    }

    @Test
    fun `preamble mentions the catalog`() {
        val msg = SystemPromptBuilder.build()
        val text = msg.content
        Assert.assertTrue(
            "preamble should reference the Custom-Pattern Catalog",
            text.contains("Custom-Pattern Catalog", ignoreCase = true)
        )
    }

    @Test
    fun `preamble mentions detection signals`() {
        val msg = SystemPromptBuilder.build()
        val text = msg.content
        Assert.assertTrue(
            "preamble should mention Filter / Interceptor / WebFilter",
            text.contains("Filter") && text.contains("WebFilter")
        )
        Assert.assertTrue(
            "preamble should mention ResponseBodyAdvice",
            text.contains("ResponseBodyAdvice")
        )
        Assert.assertTrue(
            "preamble should mention HandlerMethodArgumentResolver",
            text.contains("HandlerMethodArgumentResolver")
        )
    }

    @Test
    fun `preamble teaches both discovery tools`() {
        // The agent must know BOTH declaration styles, otherwise it produces
        // false negatives like "no Filters found" for the standard Spring Boot
        // inheritance style (`extends OncePerRequestFilter`, no `@WebFilter`).
        val msg = SystemPromptBuilder.build()
        val text = msg.content
        Assert.assertTrue(
            "preamble should mention find_classes_by_annotation",
            text.contains("find_classes_by_annotation")
        )
        Assert.assertTrue(
            "preamble should mention find_classes_by_supertype",
            text.contains("find_classes_by_supertype")
        )
        Assert.assertTrue(
            "preamble should call out the inheritance style (OncePerRequestFilter)",
            text.contains("OncePerRequestFilter")
        )
    }

    @Test
    fun `preamble mentions the contract question`() {
        val msg = SystemPromptBuilder.build()
        val text = msg.content
        Assert.assertTrue(
            "preamble should ask the invisible-contract question",
            text.contains("invisibly")
        )
    }

    @Test
    fun `preamble references knowledge-base doc names not architecture`() {
        val msg = SystemPromptBuilder.build()
        val text = msg.content
        Assert.assertTrue(
            "preamble should reference rule-guide",
            text.contains("rule-guide")
        )
        Assert.assertFalse(
            "preamble should NOT reference the removed architecture doc",
            text.contains("architecture")
        )
    }

    @Test
    fun `preamble mentions multi-app namespacing`() {
        // Per-app env-var namespacing: the preamble must teach the agent to
        // resolve a namespace key and namespace every env var in a workflow
        // bundle by that key. The full recipe lives in rule-guide.md; the
        // preamble carries only the condensed detection + on-demand-fetch
        // pointer (Decision 5).
        val msg = SystemPromptBuilder.build()
        val text = msg.content
        Assert.assertTrue(
            "preamble should mention the Multi-app namespacing subsection",
            text.contains("Multi-app namespacing")
        )
        Assert.assertTrue(
            "preamble should mention the namespace-key resolution order (module name)",
            text.contains("module name", ignoreCase = true)
        )
        Assert.assertTrue(
            "preamble should mention the namespace-key resolution order (spring.application.name)",
            text.contains("spring.application.name")
        )
        Assert.assertTrue(
            "preamble should mention the namespace-key resolution order (ask_clarification)",
            text.contains("ask_clarification")
        )
        Assert.assertTrue(
            "preamble should name get_module_dependency_graph tool",
            text.contains("get_module_dependency_graph")
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

    // ── Token-budget tripwire (review Issue #8) ──

    @Test
    fun `preamble content stays under token-budget ceiling`() {
        // The preamble is the fixed system prompt appended once at conversation start.
        // NFR-3 targets a ~600-token preamble budget. T5.1 added a condensed
        // "## Workflow-pattern detection" section (~2.8k chars). This tripwire catches
        // unexpected growth beyond the current actual length + a small headroom.
        //
        // Ceiling raised from 16_500 → 17_800 for the condensed "### Multi-app
        // namespacing" subsection added under "## Workflow-pattern detection"
        // (per Decision 5: ~600-800-char target subsection + on-demand fetch via
        // `get_plugin_doc name="rule-guide"` for the full recipe; ceiling is the new
        // actual ~16.8k + ~1k headroom).
        val msg = SystemPromptBuilder.build()
        val content = msg.content
        val ceiling = 17_800 // raised for Multi-app namespacing subsection (Decision 5): actual ~16.8k + ~1k headroom
        Assert.assertTrue(
            "Preamble content length (${content.length} chars) must stay under $ceiling chars " +
                "to stay within NFR-3's token budget. If a future section is added, raise the " +
                "ceiling to the new actual length + headroom.",
            content.length < ceiling
        )
    }
}
