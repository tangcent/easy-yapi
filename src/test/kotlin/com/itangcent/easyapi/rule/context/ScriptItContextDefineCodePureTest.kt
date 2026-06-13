package com.itangcent.easyapi.rule.context

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure tests for defineCode logic extracted from ScriptItContext.
 * The defineCode method trims source code to the declaration (before { or ;).
 */
class ScriptItContextDefineCodePureTest {

    private fun defineCode(sourceCode: String?): String? {
        val code = sourceCode?.trim() ?: return null
        val brace = code.indexOf('{').takeIf { it >= 0 }
        val semi = code.indexOf(';').takeIf { it >= 0 }
        val cut = listOfNotNull(brace, semi).minOrNull()
        return (if (cut != null) code.substring(0, cut) else code).trim().ifEmpty { null }
    }

    @Test
    fun testDefineCodeWithBrace() {
        val source = "public void hello() { return; }"
        assertEquals("public void hello()", defineCode(source))
    }

    @Test
    fun testDefineCodeWithSemicolon() {
        val source = "private String name = \"test\";"
        assertEquals("private String name = \"test\"", defineCode(source))
    }

    @Test
    fun testDefineCodeWithBothBraceAndSemicolon() {
        val source = "class Foo { String s; }"
        // Brace at 10, semicolon at 20 → min is 10
        assertEquals("class Foo", defineCode(source))
    }

    @Test
    fun testDefineCodeWithSemicolonBeforeBrace() {
        val source = "int x = 1; class Foo { }"
        // Semicolon at 9, brace at 21 → min is 9
        assertEquals("int x = 1", defineCode(source))
    }

    @Test
    fun testDefineCodeWithNoBraceOrSemicolon() {
        val source = "public abstract void hello()"
        assertEquals("public abstract void hello()", defineCode(source))
    }

    @Test
    fun testDefineCodeWithNull() {
        assertNull(defineCode(null))
    }

    @Test
    fun testDefineCodeWithEmptyString() {
        assertNull(defineCode(""))
    }

    @Test
    fun testDefineCodeWithWhitespaceOnly() {
        assertNull(defineCode("   "))
    }

    @Test
    fun testDefineCodeWithAnnotationAndBrace() {
        val source = "@Override public String toString() { return \"\"; }"
        assertEquals("@Override public String toString()", defineCode(source))
    }

    @Test
    fun testDefineCodeWithInterfaceMethod() {
        val source = "void process(String input);"
        assertEquals("void process(String input)", defineCode(source))
    }
}
