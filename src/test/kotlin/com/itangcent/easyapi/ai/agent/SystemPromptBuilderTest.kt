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
}
