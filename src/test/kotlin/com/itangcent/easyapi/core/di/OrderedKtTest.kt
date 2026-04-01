package com.itangcent.easyapi.core.di

import org.junit.Assert.*
import org.junit.Test

class OrderedTest {

    @Test
    fun testOrderedInterface() {
        class TestOrdered(override val order: Int) : Ordered

        val obj = TestOrdered(10)
        assertEquals(10, obj.order())
    }

    @Test
    fun testOrderAnnotation() {
        @Order(20)
        class AnnotatedClass

        val obj = AnnotatedClass()
        assertEquals(20, obj.order())
    }

    @Test
    fun testNoOrder() {
        class NoOrderClass

        val obj = NoOrderClass()
        assertEquals(0, obj.order())
    }

    @Test
    fun testOrderedInterfaceTakesPrecedenceOverAnnotation() {
        @Order(30)
        class BothClass(override val order: Int) : Ordered

        val obj = BothClass(40)
        assertEquals(40, obj.order())
    }

    @Test
    fun testNegativeOrder() {
        @Order(-10)
        class NegativeOrderClass

        val obj = NegativeOrderClass()
        assertEquals(-10, obj.order())
    }

    @Test
    fun testSortingByOrder() {
        @Order(30)
        class HighOrder

        @Order(10)
        class LowOrder

        @Order(20)
        class MediumOrder

        class NoOrder

        val items = listOf(HighOrder(), LowOrder(), MediumOrder(), NoOrder())
        val sorted = items.sortedBy { it.order() }

        assertEquals(0, sorted[0].order())
        assertEquals(10, sorted[1].order())
        assertEquals(20, sorted[2].order())
        assertEquals(30, sorted[3].order())
    }

    @Test
    fun testOrderedWithInterface() {
        class OrderedClass(override val order: Int) : Ordered

        val items = listOf(
            OrderedClass(100),
            OrderedClass(-100),
            OrderedClass(50)
        )
        val sorted = items.sortedBy { it.order() }

        assertEquals(-100, sorted[0].order())
        assertEquals(50, sorted[1].order())
        assertEquals(100, sorted[2].order())
    }
}
