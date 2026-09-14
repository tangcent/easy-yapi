package com.itangcent.easyapi.core.psi.type

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the derived primitive-family tables.
 *
 * These replaced four hand-maintained copies (in `SpecialTypeHandler`, `TypeNameComparison`
 * and `TypeResolver`), so the value of the test is catching *drift* — a renamed alias or a
 * family dropped from one derived view — rather than exercising clever logic.
 */
class PrimitiveFamiliesTest {

    @Test
    fun `wrapper table maps the eight boxed types to their keywords`() {
        assertEquals(
            mapOf(
                "java.lang.Boolean" to "boolean",
                "java.lang.Byte" to "byte",
                "java.lang.Character" to "char",
                "java.lang.Short" to "short",
                "java.lang.Integer" to "int",
                "java.lang.Long" to "long",
                "java.lang.Float" to "float",
                "java.lang.Double" to "double"
            ),
            PrimitiveFamilies.WRAPPER_TO_KEYWORD
        )
    }

    @Test
    fun `java lang Void is deliberately not a wrapper`() {
        assertFalse(PrimitiveFamilies.isWrapperFqn("java.lang.Void"))
        assertFalse("java.lang.Void must not be a wrapper", "java.lang.Void" in PrimitiveFamilies.WRAPPER_TO_KEYWORD)
        assertFalse("void must not be a primitive keyword", "void" in PrimitiveFamilies.PRIMITIVE_KEYWORDS)
        // ... yet `void` still resolves as a kind, which resolveFromCanonicalText relies on.
        assertEquals(PrimitiveKind.VOID, PrimitiveFamilies.KIND_BY_SPELLING["void"])
        assertEquals(PrimitiveKind.VOID, PrimitiveFamilies.KIND_BY_SPELLING["java.lang.Void"])
    }

    @Test
    fun `boxed-by-simple-name accepts the aliases and lowercase keywords`() {
        assertEquals("java.lang.Integer", PrimitiveFamilies.BOXED_BY_SIMPLE_NAME["int"])
        assertEquals("java.lang.Integer", PrimitiveFamilies.BOXED_BY_SIMPLE_NAME["integer"])
        assertEquals("java.lang.Character", PrimitiveFamilies.BOXED_BY_SIMPLE_NAME["char"])
        assertEquals("java.lang.Character", PrimitiveFamilies.BOXED_BY_SIMPLE_NAME["character"])
        assertNull("String is not a primitive family", PrimitiveFamilies.BOXED_BY_SIMPLE_NAME["string"])
        assertNull("void is excluded from the boxed table", PrimitiveFamilies.BOXED_BY_SIMPLE_NAME["void"])
    }

    @Test
    fun `kind-by-spelling accepts the keyword and the boxed FQN only`() {
        assertEquals(PrimitiveKind.INT, PrimitiveFamilies.KIND_BY_SPELLING["int"])
        assertEquals(PrimitiveKind.INT, PrimitiveFamilies.KIND_BY_SPELLING["java.lang.Integer"])
        assertEquals(PrimitiveKind.BOOLEAN, PrimitiveFamilies.KIND_BY_SPELLING["java.lang.Boolean"])
        assertNull("a bare simple name is not a key", PrimitiveFamilies.KIND_BY_SPELLING["Integer"])
        assertNull(PrimitiveFamilies.KIND_BY_SPELLING["java.lang.String"])
        assertNull(PrimitiveFamilies.KIND_BY_SPELLING["INT"])
    }

    @Test
    fun `primitive keywords are exactly the eight non-void keywords`() {
        assertEquals(
            setOf("boolean", "byte", "char", "short", "int", "long", "float", "double"),
            PrimitiveFamilies.PRIMITIVE_KEYWORDS
        )
        assertTrue(PrimitiveFamilies.isWrapperFqn("java.lang.Integer"))
        assertFalse(PrimitiveFamilies.isWrapperFqn("java.lang.String"))
    }
}
