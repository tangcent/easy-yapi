package com.itangcent.easyapi.config.parser

import org.junit.Assert.*
import org.junit.Test

class DirectiveStateUnitTest {

    @Test
    fun testDefaults() {
        val state = DirectiveState()
        assertTrue(state.resolveProperty)
        assertEquals(ResolveMultiMode.FIRST, state.resolveMulti)
        assertFalse(state.ignoreNotFoundFile)
        assertFalse(state.ignoreUnresolved)
        assertTrue(state.isActive())
    }

    @Test
    fun testPushCondition_true() {
        val state = DirectiveState()
        state.pushCondition(true)
        assertTrue(state.isActive())
    }

    @Test
    fun testPushCondition_false() {
        val state = DirectiveState()
        state.pushCondition(false)
        assertFalse(state.isActive())
    }

    @Test
    fun testNestedConditions_allTrue() {
        val state = DirectiveState()
        state.pushCondition(true)
        state.pushCondition(true)
        assertTrue(state.isActive())
    }

    @Test
    fun testNestedConditions_oneFalse() {
        val state = DirectiveState()
        state.pushCondition(true)
        state.pushCondition(false)
        assertFalse(state.isActive())
    }

    @Test
    fun testPopCondition_restoresState() {
        val state = DirectiveState()
        state.pushCondition(false)
        assertFalse(state.isActive())
        state.popCondition()
        assertTrue(state.isActive())
    }

    @Test
    fun testPopCondition_emptyStack() {
        val state = DirectiveState()
        // Should not throw
        state.popCondition()
        assertTrue(state.isActive())
    }

    @Test
    fun testSnapshot() {
        val state = DirectiveState()
        state.resolveProperty = false
        state.resolveMulti = ResolveMultiMode.LONGEST
        state.ignoreUnresolved = true
        val snap = state.snapshot()
        assertFalse(snap.resolveProperty)
        assertEquals(ResolveMultiMode.LONGEST, snap.resolveMulti)
        assertTrue(snap.ignoreUnresolved)
    }

    @Test
    fun testSnapshotDefaults() {
        val snap = DirectiveSnapshot()
        assertTrue(snap.resolveProperty)
        assertEquals(ResolveMultiMode.FIRST, snap.resolveMulti)
        assertFalse(snap.ignoreUnresolved)
    }

    @Test
    fun testResolveMultiMode_values() {
        val modes = ResolveMultiMode.values()
        assertEquals(5, modes.size)
        assertTrue(modes.contains(ResolveMultiMode.FIRST))
        assertTrue(modes.contains(ResolveMultiMode.LAST))
        assertTrue(modes.contains(ResolveMultiMode.LONGEST))
        assertTrue(modes.contains(ResolveMultiMode.SHORTEST))
        assertTrue(modes.contains(ResolveMultiMode.ERROR))
    }
}
