package com.itangcent.order

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


/**
 * Test case for [Ordered]
 */
internal class OrderedTest {

    @Test
    fun order() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, HighestOrderedBean().order())
        assertEquals(Ordered.LOWEST_PRECEDENCE, LowestOrderedBean().order())
        assertEquals(Ordered.DEFAULT_PRECEDENCE, UnOrderedBean().order())
    }

    data class MyObject(val name: String, val orderValue: Int) : Ordered {
        override fun order(): Int {
            return orderValue
        }
    }

    @Test
    fun testOrdering() {
        val objects = listOf(
            "Hello",
            42,
            MyObject("Object A", 10),
            MyObject("Object B", 5),
            MyObject("Object C", 20),
            MyObject("Object D", Ordered.HIGHEST_PRECEDENCE),
            MyObject("Object E", Ordered.LOWEST_PRECEDENCE)
        )

        // Sort the list using the order function
        val sortedObjects = objects.sortedBy { it.order() }

        // Check that the objects are sorted in the expected order
        assertEquals("Object D", (sortedObjects[0] as MyObject).name)
        assertEquals("Hello", sortedObjects[1])
        assertEquals(42, sortedObjects[2])
        assertEquals("Object B", (sortedObjects[3] as MyObject).name)
        assertEquals("Object A", (sortedObjects[4] as MyObject).name)
        assertEquals("Object C", (sortedObjects[5] as MyObject).name)
        assertEquals("Object E", (sortedObjects[6] as MyObject).name)
    }
}

class HighestOrderedBean : Ordered {
    override fun order(): Int {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

@Order(Ordered.LOWEST_PRECEDENCE)
class LowestOrderedBean

class UnOrderedBean