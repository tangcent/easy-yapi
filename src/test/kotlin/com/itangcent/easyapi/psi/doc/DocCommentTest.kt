package com.itangcent.easyapi.psi.doc

import org.junit.Assert.*
import org.junit.Test

class DocCommentTest {

    @Test
    fun testDefaultValues() {
        val comment = DocComment("/** Hello */")
        assertEquals("/** Hello */", comment.text)
        assertEquals(emptyList<DocTag>(), comment.tags)
    }

    @Test
    fun testWithTags() {
        val tags = listOf(
            DocTag("param", "id the user ID"),
            DocTag("return", "the user")
        )
        val comment = DocComment("/** Gets user by ID. */", tags)
        assertEquals("/** Gets user by ID. */", comment.text)
        assertEquals(2, comment.tags.size)
        assertEquals("param", comment.tags[0].name)
        assertEquals("id the user ID", comment.tags[0].value)
        assertEquals("return", comment.tags[1].name)
    }

    @Test
    fun testEquality() {
        val tags = listOf(DocTag("param", "id"))
        val c1 = DocComment("/** text */", tags)
        val c2 = DocComment("/** text */", tags)
        assertEquals(c1, c2)
    }

    @Test
    fun testInequality_differentText() {
        val c1 = DocComment("/** text1 */")
        val c2 = DocComment("/** text2 */")
        assertNotEquals(c1, c2)
    }

    @Test
    fun testInequality_differentTags() {
        val c1 = DocComment("/** text */", listOf(DocTag("param", "a")))
        val c2 = DocComment("/** text */", listOf(DocTag("param", "b")))
        assertNotEquals(c1, c2)
    }

    @Test
    fun testHashCode() {
        val c1 = DocComment("/** text */", listOf(DocTag("param", "id")))
        val c2 = DocComment("/** text */", listOf(DocTag("param", "id")))
        assertEquals(c1.hashCode(), c2.hashCode())
    }

    @Test
    fun testCopy() {
        val original = DocComment("/** text */", listOf(DocTag("param", "id")))
        val copy = original.copy(text = "/** new text */")
        assertEquals("/** new text */", copy.text)
        assertEquals(original.tags, copy.tags)
    }
}

class DocTagTest {

    @Test
    fun testBasicProperties() {
        val tag = DocTag("param", "id the user ID")
        assertEquals("param", tag.name)
        assertEquals("id the user ID", tag.value)
    }

    @Test
    fun testEmptyValue() {
        val tag = DocTag("deprecated", "")
        assertEquals("deprecated", tag.name)
        assertEquals("", tag.value)
    }

    @Test
    fun testEquality() {
        val t1 = DocTag("param", "id")
        val t2 = DocTag("param", "id")
        assertEquals(t1, t2)
    }

    @Test
    fun testInequality_differentName() {
        val t1 = DocTag("param", "id")
        val t2 = DocTag("return", "id")
        assertNotEquals(t1, t2)
    }

    @Test
    fun testInequality_differentValue() {
        val t1 = DocTag("param", "id")
        val t2 = DocTag("param", "name")
        assertNotEquals(t1, t2)
    }

    @Test
    fun testHashCode() {
        val t1 = DocTag("param", "id")
        val t2 = DocTag("param", "id")
        assertEquals(t1.hashCode(), t2.hashCode())
    }

    @Test
    fun testCopy() {
        val original = DocTag("param", "id")
        val copy = original.copy(value = "name")
        assertEquals("param", copy.name)
        assertEquals("name", copy.value)
    }
}
