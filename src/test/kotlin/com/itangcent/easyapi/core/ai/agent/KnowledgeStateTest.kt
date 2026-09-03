package com.itangcent.easyapi.core.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeStateTest {

    @Test
    fun `empty knowledge state renders empty string`() {
        val state = KnowledgeState()
        assertEquals("", state.render())
        assertEquals(0, state.estimatedTokens())
        assertFalse(state.hasContent())
    }

    @Test
    fun `upsert adds new entries to section`() {
        val state = KnowledgeState()
        val result = state.upsert("§keys", listOf(
            KnowledgeState.Entry("key1", "key1 | summary 1"),
            KnowledgeState.Entry("key2", "key2 | summary 2")
        ))

        assertTrue(result is KnowledgeState.UpsertResult.Changed)
        val changed = result as KnowledgeState.UpsertResult.Changed
        assertEquals(2, changed.added.size)
        assertEquals(0, changed.updated.size)
        assertEquals(0, changed.unchanged)

        val rendered = state.render()
        assertTrue(rendered.contains("§keys (v:1)"))
        assertTrue(rendered.contains("key1 | summary 1"))
        assertTrue(rendered.contains("key2 | summary 2"))
        assertTrue(state.hasContent())
    }

    @Test
    fun `upsert unchanged entries returns NoChange`() {
        val state = KnowledgeState()
        state.upsert("§keys", listOf(
            KnowledgeState.Entry("key1", "key1 | summary 1")
        ))

        val result = state.upsert("§keys", listOf(
            KnowledgeState.Entry("key1", "key1 | summary 1")
        ))

        assertTrue(result is KnowledgeState.UpsertResult.NoChange)
        assertEquals(1, (result as KnowledgeState.UpsertResult.NoChange).unchanged)
        assertEquals(1, state.sectionVersion("§keys"))
    }

    @Test
    fun `upsert updates existing entry when contentHash changes`() {
        val state = KnowledgeState()
        state.upsert("§keys", listOf(
            KnowledgeState.Entry("key1", "key1 | old summary")
        ))

        val result = state.upsert("§keys", listOf(
            KnowledgeState.Entry("key1", "key1 | new summary")
        ))

        assertTrue(result is KnowledgeState.UpsertResult.Changed)
        val changed = result as KnowledgeState.UpsertResult.Changed
        assertEquals(0, changed.added.size)
        assertEquals(1, changed.updated.size)
        assertEquals(2, state.sectionVersion("§keys"))

        val rendered = state.render()
        assertTrue(rendered.contains("new summary"))
        assertFalse(rendered.contains("old summary"))
    }

    @Test
    fun `contentHash is derived from the rendered line`() {
        // Callers used to pass the hash by hand; a wrong (or constant) hash
        // silently disabled dedup. It is now a property of the content.
        assertEquals(
            "same line".hashCode(),
            KnowledgeState.Entry("a", "same line").contentHash
        )
        assertEquals(
            KnowledgeState.Entry("a", "same line").contentHash,
            KnowledgeState.Entry("b", "same line").contentHash
        )
    }

    @Test
    fun `same content under a different id is deduplicated`() {
        val state = KnowledgeState()
        state.upsert("§keyContexts", listOf(KnowledgeState.Entry("param.doc", "param.doc | general | dynamic")))

        val result = state.upsert("§keyContexts", listOf(KnowledgeState.Entry("doc.param", "param.doc | general | dynamic")))

        assertTrue("content-level dedup should make this a no-op, was $result", result is KnowledgeState.UpsertResult.NoChange)
        // One entry, one line — the alias did not append a duplicate.
        assertEquals(1, state.entries("§keyContexts").size)
        assertEquals(1, countOccurrences(state.render(), "param.doc | general | dynamic"))
        assertEquals(1, state.sectionVersion("§keyContexts"))
    }

    @Test
    fun `content dedup still lets a changed line through`() {
        val state = KnowledgeState()
        state.upsert("§keyContexts", listOf(KnowledgeState.Entry("param.doc", "param.doc | general | dynamic")))
        // B is content-deduped against A ...
        state.upsert("§keyContexts", listOf(KnowledgeState.Entry("doc.param", "param.doc | general | dynamic")))
        // ... then B's own content changes: it must now be rendered.
        val result = state.upsert("§keyContexts", listOf(KnowledgeState.Entry("doc.param", "param.doc | general | literal")))

        assertTrue(result is KnowledgeState.UpsertResult.Changed)
        val rendered = state.render()
        assertTrue(rendered.contains("param.doc | general | dynamic"))
        assertTrue(rendered.contains("param.doc | general | literal"))
        assertEquals(2, state.entries("§keyContexts").size)
    }

    @Test
    fun `invalidate clears section and increments version`() {
        val state = KnowledgeState()
        state.upsert("§keys", listOf(
            KnowledgeState.Entry("key1", "key1 | summary")
        ))
        assertEquals(1, state.sectionVersion("§keys"))
        assertTrue(state.hasContent())

        state.invalidate("§keys")
        assertEquals(2, state.sectionVersion("§keys"))
        val rendered = state.render()
        assertFalse(rendered.contains("key1"))
        // section exists but is empty, so the overall render still has the header
        // but no entries are present
    }

    @Test
    fun `invalidate drops the content index so the same content can be re-added`() {
        val state = KnowledgeState()
        state.upsert("§keys", listOf(KnowledgeState.Entry("key1", "key1 | summary")))
        state.invalidate("§keys")

        val result = state.upsert("§keys", listOf(KnowledgeState.Entry("key1", "key1 | summary")))
        assertTrue("after invalidate the content index must be empty, was $result", result is KnowledgeState.UpsertResult.Changed)
        assertEquals(1, state.entries("§keys").size)
    }

    @Test
    fun `estimatedTokens approximates based on character count`() {
        val state = KnowledgeState()
        state.upsert("§keys", listOf(
            KnowledgeState.Entry("key1", "x".repeat(100)),
            KnowledgeState.Entry("key2", "y".repeat(100))
        ))

        // ~200 chars + header chars = ~50 tokens
        val tokens = state.estimatedTokens()
        assertTrue(tokens > 30)
        assertTrue(tokens < 80)
    }

    @Test
    fun `clear removes all sections`() {
        val state = KnowledgeState()
        state.upsert("§keys", listOf(KnowledgeState.Entry("k1", "line1")))
        state.upsert("§objects", listOf(KnowledgeState.Entry("o1", "obj1")))
        assertTrue(state.hasContent())

        state.clear()
        assertFalse(state.hasContent())
        assertEquals("", state.render())
        assertEquals(0, state.estimatedTokens())
    }

    @Test
    fun `multiple sections rendered in insertion order`() {
        val state = KnowledgeState()
        state.upsert("§keys", listOf(KnowledgeState.Entry("a", "a")))
        state.upsert("§objects", listOf(KnowledgeState.Entry("b", "b")))

        val rendered = state.render()
        val idxKeys = rendered.indexOf("§keys")
        val idxObjects = rendered.indexOf("§objects")
        assertTrue(idxKeys < idxObjects)
    }

    private fun countOccurrences(haystack: String, needle: String): Int =
        haystack.split(needle).size - 1
}
