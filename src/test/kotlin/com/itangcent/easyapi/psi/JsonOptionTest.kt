package com.itangcent.easyapi.psi

import junit.framework.TestCase
import org.junit.Test

class JsonOptionTest : TestCase() {

    fun testNoneOption() {
        assertEquals(0b0000, JsonOption.NONE)
    }

    fun testReadCommentOption() {
        assertEquals(0b0001, JsonOption.READ_COMMENT)
    }

    fun testReadGetterOption() {
        assertEquals(0b0010, JsonOption.READ_GETTER)
    }

    fun testReadSetterOption() {
        assertEquals(0b0100, JsonOption.READ_SETTER)
    }

    fun testAllOption() {
        assertEquals(JsonOption.READ_COMMENT or JsonOption.READ_GETTER or JsonOption.READ_SETTER, JsonOption.ALL)
    }

    fun testReadGetterOrSetterOption() {
        assertEquals(JsonOption.READ_GETTER or JsonOption.READ_SETTER, JsonOption.READ_GETTER_OR_SETTER)
    }

    fun testHasWithMatchingFlag() {
        val option = JsonOption.READ_COMMENT or JsonOption.READ_GETTER
        assertTrue(JsonOption.has(option, JsonOption.READ_COMMENT))
        assertTrue(JsonOption.has(option, JsonOption.READ_GETTER))
    }

    fun testHasWithNonMatchingFlag() {
        val option = JsonOption.READ_COMMENT or JsonOption.READ_GETTER
        assertFalse(JsonOption.has(option, JsonOption.READ_SETTER))
    }

    fun testHasWithNoneOption() {
        assertFalse(JsonOption.has(JsonOption.NONE, JsonOption.READ_COMMENT))
        assertFalse(JsonOption.has(JsonOption.NONE, JsonOption.READ_GETTER))
        assertFalse(JsonOption.has(JsonOption.NONE, JsonOption.READ_SETTER))
    }

    fun testHasWithAllOption() {
        assertTrue(JsonOption.has(JsonOption.ALL, JsonOption.READ_COMMENT))
        assertTrue(JsonOption.has(JsonOption.ALL, JsonOption.READ_GETTER))
        assertTrue(JsonOption.has(JsonOption.ALL, JsonOption.READ_SETTER))
    }

    fun testHasWithCombinedFlags() {
        val option = JsonOption.READ_COMMENT or JsonOption.READ_GETTER or JsonOption.READ_SETTER
        assertTrue(JsonOption.has(option, JsonOption.READ_COMMENT))
        assertTrue(JsonOption.has(option, JsonOption.READ_GETTER))
        assertTrue(JsonOption.has(option, JsonOption.READ_SETTER))
    }

    fun testHasWithSingleFlag() {
        assertTrue(JsonOption.has(JsonOption.READ_COMMENT, JsonOption.READ_COMMENT))
        assertFalse(JsonOption.has(JsonOption.READ_COMMENT, JsonOption.READ_GETTER))
    }

    fun testOptionCombinationPreservesFlags() {
        val combined = JsonOption.READ_GETTER or JsonOption.READ_SETTER
        assertTrue(JsonOption.has(combined, JsonOption.READ_GETTER))
        assertTrue(JsonOption.has(combined, JsonOption.READ_SETTER))
        assertFalse(JsonOption.has(combined, JsonOption.READ_COMMENT))
    }
}
