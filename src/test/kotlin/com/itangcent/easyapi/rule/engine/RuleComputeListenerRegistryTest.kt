package com.itangcent.easyapi.rule.engine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RuleComputeListenerRegistryTest {

    private lateinit var registry: RuleComputeListenerRegistry

    @Before
    fun setUp() {
        registry = RuleComputeListenerRegistry()
    }

    @Test
    fun testRegisterAndNotify() {
        val events = mutableListOf<Triple<String, Any?, Any?>>()
        registry.register(object : RuleComputeListener {
            override fun onRuleComputed(key: String, element: com.intellij.psi.PsiElement?, result: Any?) {
                events.add(Triple(key, element, result))
            }
        })

        registry.notify("api.name", null, "getUser")
        assertEquals(1, events.size)
        assertEquals("api.name", events[0].first)
        assertNull(events[0].second)
        assertEquals("getUser", events[0].third)
    }

    @Test
    fun testMultipleListeners() {
        var count1 = 0
        var count2 = 0
        registry.register(object : RuleComputeListener {
            override fun onRuleComputed(key: String, element: com.intellij.psi.PsiElement?, result: Any?) {
                count1++
            }
        })
        registry.register(object : RuleComputeListener {
            override fun onRuleComputed(key: String, element: com.intellij.psi.PsiElement?, result: Any?) {
                count2++
            }
        })

        registry.notify("key", null, "value")
        assertEquals(1, count1)
        assertEquals(1, count2)
    }

    @Test
    fun testNotify_noListeners() {
        // Should not throw
        registry.notify("key", null, "value")
    }

    @Test
    fun testNotify_multipleEvents() {
        var count = 0
        registry.register(object : RuleComputeListener {
            override fun onRuleComputed(key: String, element: com.intellij.psi.PsiElement?, result: Any?) {
                count++
            }
        })

        registry.notify("key1", null, "v1")
        registry.notify("key2", null, "v2")
        registry.notify("key3", null, "v3")
        assertEquals(3, count)
    }
}
