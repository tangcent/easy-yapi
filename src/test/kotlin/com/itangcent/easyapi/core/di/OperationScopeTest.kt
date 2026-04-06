package com.itangcent.easyapi.core.di

import org.junit.Assert.*
import org.junit.Test

class OperationScopeTest {

    @Test
    fun testBuilderBind() {
        val scope = OperationScope.builder()
            .bind("test string")
            .bind(42)
            .build()

        assertEquals("test string", scope.get(String::class))
        assertEquals(42, scope.get(Int::class))
    }

    @Test
    fun testBuilderBindWithClass() {
        val scope = OperationScope.builder()
            .bind(Number::class, 42)
            .build()

        assertEquals(42, scope.get(Number::class))
    }

    @Test
    fun testBuilderBindLazy() {
        var created = false
        val scope = OperationScope.builder()
            .bindLazy(String::class) {
                created = true
                "lazy value"
            }
            .build()

        assertFalse("Should not be created yet", created)
        val value = scope.get(String::class)
        assertTrue("Should be created on access", created)
        assertEquals("lazy value", value)
    }

    @Test
    fun testGetOrNull() {
        val scope = OperationScope.builder()
            .bind("test")
            .build()

        assertEquals("test", scope.getOrNull(String::class))
        assertNull(scope.getOrNull(Int::class))
    }

    @Test
    fun testContains() {
        val scope = OperationScope.builder()
            .bind("test")
            .build()

        assertTrue(scope.contains(String::class))
        assertFalse(scope.contains(Int::class))
    }

    @Test
    fun testGetThrowsWhenNotFound() {
        val scope = OperationScope.builder().build()

        assertThrows(OperationScopeException::class.java) {
            scope.get(String::class)
        }
    }

    @Test
    fun testExtensionFunction() {
        val scope = OperationScope.builder()
            .bind("test value")
            .build()

        val value: String = scope.get(String::class)
        assertEquals("test value", value)
    }

    @Test
    fun testExtensionFunctionOrNull() {
        val scope = OperationScope.builder()
            .bind("test value")
            .build()

        val value: String? = scope.getOrNull(String::class)
        assertEquals("test value", value)

        val notFound: Int? = scope.getOrNull(Int::class)
        assertNull(notFound)
    }

    @Test
    fun testMultipleBindings() {
        data class TestClass(val name: String, val value: Int)

        val scope = OperationScope.builder()
            .bind(TestClass("test", 123))
            .bind("string value")
            .bind(42)
            .bind(3.14)
            .build()

        val testClass = scope.get(TestClass::class)
        assertEquals("test", testClass.name)
        assertEquals(123, testClass.value)

        assertEquals("string value", scope.get(String::class))
        assertEquals(42, scope.get(Int::class))
        assertEquals(3.14, scope.get(Double::class), 0.001)
    }

    @Test
    fun testLazyValueIsCached() {
        var callCount = 0
        val scope = OperationScope.builder()
            .bindLazy(String::class) {
                callCount++
                "lazy value"
            }
            .build()

        scope.get(String::class)
        scope.get(String::class)
        scope.get(String::class)

        assertEquals("Lazy should only be computed once", 1, callCount)
    }
}
