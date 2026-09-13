package com.itangcent.easyapi.core.psi.type

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TypeNameComparison] — the coarse "same kind" comparison behind the Case-2
 * enum type-match heuristic.
 *
 * `EnumResolutionTest.Case2_SeeEnumAutoMatch` covers the heuristic end to end
 * (`int type` + `@see UserType` → `UserType.code`); this file pins the rules it relies
 * on, including the ones that are deliberately **not** implemented.
 */
class TypeNameComparisonTest {

    @Test
    fun `primitives and boxed forms are one kind across Java and Kotlin spellings`() {
        val spellings = listOf("int", "Integer", "java.lang.Integer", "Int", "kotlin.Int", "Int?")
        spellings.forEach { a ->
            spellings.forEach { b ->
                assertTrue("$a should be the same kind as $b", TypeNameComparison.isSameKind(a, b))
            }
        }
        assertTrue(TypeNameComparison.isSameKind("long", "kotlin.Long?"))
        assertTrue(TypeNameComparison.isSameKind("java.lang.Boolean", "Boolean"))
        assertTrue(TypeNameComparison.isSameKind("char", "Character"))
        assertTrue(TypeNameComparison.isSameKind("boolean", "Boolean?"))
    }

    @Test
    fun `primitive kinds stay distinct - there is no numeric widening`() {
        assertFalse(TypeNameComparison.isSameKind("byte", "Integer"))
        assertFalse(TypeNameComparison.isSameKind("long", "Int"))
        assertFalse(TypeNameComparison.isSameKind("int", "Double"))
    }

    @Test
    fun `non-primitive kinds match on the simple name, case-sensitively`() {
        assertTrue(TypeNameComparison.isSameKind("String", "java.lang.String"))
        assertTrue(TypeNameComparison.isSameKind("String", "kotlin.String"))
        assertTrue(TypeNameComparison.isSameKind("com.acme.OrderStatus", "OrderStatus"))
        assertTrue(TypeNameComparison.isSameKind("java.util.Map.Entry", "Entry"))
        assertFalse(TypeNameComparison.isSameKind("String", "string"))
        assertFalse(TypeNameComparison.isSameKind("OrderStatus", "OrderState"))
    }

    @Test
    fun `type arguments are not part of the kind`() {
        // Deliberate: arguments are spelled inconsistently (raw / wildcard / star /
        // unresolved), and losing a candidate costs more than adding one — the caller
        // breaks remaining ties by field name.
        assertTrue(TypeNameComparison.isSameKind("java.util.List<java.lang.Integer>", "List<Int>"))
        assertTrue(TypeNameComparison.isSameKind("List<Int>", "List<String>"))
        assertTrue(TypeNameComparison.isSameKind("java.util.List", "List<Int>"))
        assertTrue(TypeNameComparison.isSameKind("List<? extends Integer>", "List<Integer>"))
        assertTrue(TypeNameComparison.isSameKind("List<*>", "kotlin.collections.List<String>?"))
        assertTrue(TypeNameComparison.isSameKind("Map<String, List<Int>>", "java.util.Map"))
    }

    @Test
    fun `different outer types stay different`() {
        assertFalse(TypeNameComparison.isSameKind("List<Int>", "Set<Int>"))
        assertFalse(TypeNameComparison.isSameKind("Map<String,Int>", "List<Int>"))
        assertFalse(TypeNameComparison.isSameKind("Collection<String>", "List<String>"))
        assertFalse(TypeNameComparison.isSameKind("Object", "String"))
        assertFalse(TypeNameComparison.isSameKind("Serializable", "String"))
    }

    @Test
    fun `array-ness is significant but Kotlin array spellings are not bridged`() {
        assertTrue(TypeNameComparison.isSameKind("String[]", "java.lang.String[]"))
        assertTrue(TypeNameComparison.isSameKind("int[]", "Int[]"))
        assertFalse(TypeNameComparison.isSameKind("String[]", "String"))
        // Documented non-goal: `Array<X>` / `IntArray` are not bridged to the Java spellings.
        assertFalse(TypeNameComparison.isSameKind("Array<String>", "String[]"))
        assertFalse(TypeNameComparison.isSameKind("IntArray", "int[]"))
    }

    @Test
    fun `kindOf returns the raw boxed key`() {
        assertEquals("java.lang.Integer", TypeNameComparison.kindOf("Int?"))
        assertEquals("java.lang.Integer", TypeNameComparison.kindOf("java.lang.Integer"))
        assertEquals("java.lang.Long", TypeNameComparison.kindOf("kotlin.Long"))
        assertEquals("String", TypeNameComparison.kindOf("kotlin.String"))
        assertEquals("String[]", TypeNameComparison.kindOf("java.lang.String[]"))
        assertEquals("java.lang.Integer[]", TypeNameComparison.kindOf("int[]"))
        assertEquals("List", TypeNameComparison.kindOf("java.util.List"))
        assertEquals("List", TypeNameComparison.kindOf("kotlin.collections.List<Int>?"))
        assertEquals("Map", TypeNameComparison.kindOf("java.util.Map<String, List<Int>>"))
        assertEquals("Entry", TypeNameComparison.kindOf("java.util.Map.Entry<K,V>"))
    }
}
