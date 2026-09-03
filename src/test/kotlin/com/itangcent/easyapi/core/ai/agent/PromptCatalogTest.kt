package com.itangcent.easyapi.core.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PromptCatalog].
 *
 * Split into two groups:
 * 1. **Pure parsing tests** — exercise [PromptCatalog.parseFrontMatter],
 *    [PromptCatalog.buildEntry], and [PromptCatalog.deriveCategory] with
 *    synthetic content. No classpath access required.
 * 2. **Classpath integration tests** — exercise [PromptCatalog.list],
 *    [PromptCatalog.entry], [PromptCatalog.body], and [PromptCatalog.listFor]
 *    against the real catalog files shipped under `src/main/resources/ai/`.
 */
class PromptCatalogTest {

    // ===================================================================
    // Pure parsing tests
    // ===================================================================

    @Test
    fun parseFrontMatter_wellFormedHeader_parsesHeaderAndBody() {
        val content = """
            ---
            id: test-id
            title: Test Title
            cue: when to use
            ---
            # Body content
            Some text here.
        """.trimIndent()
        val parsed = PromptCatalog.parseFrontMatter(content)
        assertNotNull(parsed)
        assertEquals("test-id", parsed!!.header["id"])
        assertEquals("Test Title", parsed.header["title"])
        assertEquals("when to use", parsed.header["cue"])
        assertTrue("body should contain the heading", parsed.body.contains("# Body content"))
        assertTrue("body should contain the text", parsed.body.contains("Some text here."))
    }

    @Test
    fun parseFrontMatter_withOptionalScopeFields_parsesAllFields() {
        val content = """
            ---
            id: postman.test
            key: postman.test
            title: Postman test
            cue: assertion
            channel: postman
            framework: springmvc
            ---
            body
        """.trimIndent()
        val parsed = PromptCatalog.parseFrontMatter(content)!!
        assertEquals("postman.test", parsed.header["id"])
        assertEquals("postman.test", parsed.header["key"])
        assertEquals("postman", parsed.header["channel"])
        assertEquals("springmvc", parsed.header["framework"])
    }

    @Test
    fun parseFrontMatter_missingOpeningDelimiter_returnsNull() {
        val content = "id: test\ntitle: Test\n---\nbody"
        assertNull(PromptCatalog.parseFrontMatter(content))
    }

    @Test
    fun parseFrontMatter_missingClosingDelimiter_returnsNull() {
        val content = "---\nid: test\ntitle: Test\nbody without closing"
        assertNull(PromptCatalog.parseFrontMatter(content))
    }

    @Test
    fun parseFrontMatter_malformedYaml_returnsNull() {
        val content = """
            ---
            id: [unclosed bracket
            title: Test
            ---
            body
        """.trimIndent()
        assertNull(PromptCatalog.parseFrontMatter(content))
    }

    @Test
    fun parseFrontMatter_emptyHeader_returnsEmptyMap() {
        val content = """
            ---
            ---
            body
        """.trimIndent()
        val parsed = PromptCatalog.parseFrontMatter(content)
        assertNotNull(parsed)
        assertTrue(parsed!!.header.isEmpty())
        assertEquals("body", parsed.body)
    }

    @Test
    fun buildEntry_allRequiredFieldsPresent_buildsEntry() {
        val parsed = PromptCatalog.ParsedCatalogFile(
            header = mapOf(
                "id" to "postman.test",
                "title" to "Postman test",
                "cue" to "assertion",
                "key" to "postman.test",
                "channel" to "postman"
            ),
            body = "body"
        )
        val entry = PromptCatalog.buildEntry("ai/key-guides/postman.test.md", parsed)
        assertNotNull(entry)
        assertEquals("key-guides", entry!!.category)
        assertEquals("postman.test", entry.id)
        assertEquals("Postman test", entry.title)
        assertEquals("assertion", entry.cue)
        assertEquals("postman.test", entry.key)
        assertEquals("postman", entry.scope.channel)
    }

    @Test
    fun buildEntry_missingId_returnsNull() {
        val parsed = PromptCatalog.ParsedCatalogFile(
            header = mapOf("title" to "T", "cue" to "C"),
            body = ""
        )
        assertNull(PromptCatalog.buildEntry("ai/key-guides/test.md", parsed))
    }

    @Test
    fun buildEntry_missingTitle_returnsNull() {
        val parsed = PromptCatalog.ParsedCatalogFile(
            header = mapOf("id" to "test", "cue" to "C"),
            body = ""
        )
        assertNull(PromptCatalog.buildEntry("ai/key-guides/test.md", parsed))
    }

    @Test
    fun buildEntry_missingCue_returnsNull() {
        val parsed = PromptCatalog.ParsedCatalogFile(
            header = mapOf("id" to "test", "title" to "T"),
            body = ""
        )
        assertNull(PromptCatalog.buildEntry("ai/key-guides/test.md", parsed))
    }

    @Test
    fun buildEntry_derivesCategoryFromPath() {
        val parsed = PromptCatalog.ParsedCatalogFile(
            header = mapOf("id" to "x", "title" to "T", "cue" to "C"),
            body = ""
        )
        assertEquals("detection", PromptCatalog.buildEntry("ai/detection/x.md", parsed)!!.category)
        assertEquals("key-guides", PromptCatalog.buildEntry("ai/key-guides/x.md", parsed)!!.category)
    }

    @Test
    fun buildEntry_unrecognizedPathPattern_returnsNull() {
        val parsed = PromptCatalog.ParsedCatalogFile(
            header = mapOf("id" to "x", "title" to "T", "cue" to "C"),
            body = ""
        )
        assertNull(PromptCatalog.buildEntry("other/x.md", parsed))
    }

    @Test
    fun deriveCategory_validPaths() {
        assertEquals("detection", PromptCatalog.deriveCategory("ai/detection/foo.md"))
        assertEquals("key-guides", PromptCatalog.deriveCategory("ai/key-guides/bar.md"))
    }

    @Test
    fun deriveCategory_invalidPaths() {
        assertNull(PromptCatalog.deriveCategory("detection/foo.md"))
        assertNull(PromptCatalog.deriveCategory("ai"))
        assertNull(PromptCatalog.deriveCategory(""))
    }

    @Test
    fun catalogScope_matchesCorrectly() {
        val scoped = CatalogScope(channel = "postman", framework = "springmvc")
        assertTrue(scoped.matches(setOf("postman"), emptySet(), setOf("springmvc")))
        assertFalse(scoped.matches(emptySet(), emptySet(), setOf("springmvc")))
        assertFalse(scoped.matches(setOf("postman"), emptySet(), emptySet()))
    }

    @Test
    fun catalogScope_nullFieldsAlwaysMatch() {
        val unscoped = CatalogScope()
        assertTrue(unscoped.matches(emptySet(), emptySet(), emptySet()))
        assertTrue(unscoped.matches(setOf("a"), setOf("b"), setOf("c")))
    }

    // ===================================================================
    // Classpath integration tests (use the real catalog files from A1)
    // ===================================================================

    @Test
    fun list_detectionCategory_returnsSeededEntries() {
        val entries = PromptCatalog.list("detection")
        assertTrue("detection list should not be empty", entries.isNotEmpty())
        val ids = entries.map { it.id }.toSet()
        assertTrue("spring-filters-interceptors missing", "spring-filters-interceptors" in ids)
        assertTrue("static-auth missing", "static-auth" in ids)
        assertTrue("hmac-signing missing", "hmac-signing" in ids)
    }

    @Test
    fun list_keyGuidesCategory_returnsSeededEntries() {
        val entries = PromptCatalog.list("key-guides")
        assertTrue("key-guides list should not be empty", entries.isNotEmpty())
        val ids = entries.map { it.id }.toSet()
        assertTrue("postman.test missing", "postman.test" in ids)
        assertTrue("method.additional.header missing", "method.additional.header" in ids)
        assertTrue("json.additional.field missing", "json.additional.field" in ids)
    }

    @Test
    fun list_unknownCategory_returnsEmpty() {
        assertTrue(PromptCatalog.list("nonexistent").isEmpty())
    }

    @Test
    fun entry_existingId_returnsEntry() {
        val entry = PromptCatalog.entry("key-guides", "postman.test")
        assertNotNull(entry)
        assertEquals("postman.test", entry!!.id)
        assertEquals("Postman test script", entry.title)
        assertEquals("postman.test", entry.key)
        assertEquals("postman", entry.scope.channel)
    }

    @Test
    fun entry_unknownId_returnsNull() {
        assertNull(PromptCatalog.entry("key-guides", "does.not.exist"))
    }

    @Test
    fun body_existingId_returnsNonEmptyBody() {
        val body = PromptCatalog.body("key-guides", "postman.test")
        assertNotNull(body)
        assertTrue("body should be non-empty", body!!.isNotBlank())
        // The postman.test body mentions "pm.response" in its recipe.
        assertTrue("body should contain recipe content", body.contains("pm.response"))
    }

    @Test
    fun body_unknownId_returnsNull() {
        assertNull(PromptCatalog.body("key-guides", "does.not.exist"))
    }

    @Test
    fun body_stripsFrontMatter() {
        // The body must NOT contain the YAML header.
        val body = PromptCatalog.body("detection", "static-auth")!!
        assertFalse("body should not contain front-matter", body.contains("---"))
        assertFalse("body should not contain 'id:'", body.contains("id:"))
    }

    @Test
    fun listFor_filtersByChannelScope() {
        // A channel: postman key-guide file should appear with postman active...
        val withPostman = PromptCatalog.listFor(
            "key-guides",
            activeChannels = setOf("postman"),
            activeFormats = emptySet(),
            activeFrameworks = emptySet()
        )
        val withPostmanIds = withPostman.map { it.id }.toSet()
        assertTrue(
            "postman.test should appear when postman is active",
            "postman.test" in withPostmanIds
        )

        // ...and be absent when postman is not active.
        val withoutPostman = PromptCatalog.listFor(
            "key-guides",
            activeChannels = emptySet(),
            activeFormats = emptySet(),
            activeFrameworks = emptySet()
        )
        val withoutPostmanIds = withoutPostman.map { it.id }.toSet()
        assertFalse(
            "postman.test should be absent when postman is not active",
            "postman.test" in withoutPostmanIds
        )
    }

    @Test
    fun listFor_unscopedEntriesAlwaysAppear() {
        // Entries with no scope fields should appear regardless of active sets.
        val unscoped = PromptCatalog.listFor(
            "key-guides",
            activeChannels = emptySet(),
            activeFormats = emptySet(),
            activeFrameworks = emptySet()
        )
        val unscopedIds = unscoped.map { it.id }.toSet()
        // json.additional.field has no scope fields (no channel/format/framework).
        assertTrue(
            "json.additional.field (unscoped) should always appear",
            "json.additional.field" in unscopedIds
        )
    }

    @Test
    fun listFor_filtersByFrameworkScope() {
        // detection files with framework: springmvc should appear with
        // springmvc active and be absent without.
        val withSpring = PromptCatalog.listFor(
            "detection",
            activeChannels = emptySet(),
            activeFormats = emptySet(),
            activeFrameworks = setOf("springmvc")
        )
        val withSpringIds = withSpring.map { it.id }.toSet()
        assertTrue(
            "spring-filters-interceptors (framework: springmvc) should appear",
            "spring-filters-interceptors" in withSpringIds
        )

        val withoutSpring = PromptCatalog.listFor(
            "detection",
            activeChannels = emptySet(),
            activeFormats = emptySet(),
            activeFrameworks = emptySet()
        )
        val withoutSpringIds = withoutSpring.map { it.id }.toSet()
        assertFalse(
            "spring-filters-interceptors should be absent without springmvc",
            "spring-filters-interceptors" in withoutSpringIds
        )
    }
}
