package com.itangcent.easyapi.core.event

import org.junit.Assert.*
import org.junit.Test

class EventKeysTest {

    @Test
    fun testOnCompleted() {
        assertEquals("ON_COMPLETED", EventKeys.ON_COMPLETED)
    }

    @Test
    fun testOnCompleted_isConstant() {
        // Verify it's a compile-time constant (const val)
        val field = EventKeys::class.java.getDeclaredField("ON_COMPLETED")
        assertTrue(java.lang.reflect.Modifier.isStatic(field.modifiers))
        assertTrue(java.lang.reflect.Modifier.isFinal(field.modifiers))
    }
}
