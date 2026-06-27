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
}
