package com.itangcent.order

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


/**
 * Test case ot [Ordered]
 */
internal class OrderedTest {

    @Test
    fun order() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, HighestOrderedBean().order())
        assertEquals(Ordered.LOWEST_PRECEDENCE, LowestOrderedBean().order())
        assertEquals(Ordered.DEFAULT_PRECEDENCE, UnOrderedBean().order())
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