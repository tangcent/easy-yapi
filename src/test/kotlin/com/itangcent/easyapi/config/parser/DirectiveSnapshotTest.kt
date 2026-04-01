package com.itangcent.easyapi.config.parser

import org.junit.Assert.*
import org.junit.Test

class DirectiveSnapshotTest {

    @Test
    fun testDefaultValues() {
        val snapshot = DirectiveSnapshot()
        assertTrue(snapshot.resolveProperty)
        assertEquals(ResolveMultiMode.FIRST, snapshot.resolveMulti)
        assertFalse(snapshot.ignoreUnresolved)
    }

    @Test
    fun testCustomValues() {
        val snapshot = DirectiveSnapshot(
            resolveProperty = false,
            resolveMulti = ResolveMultiMode.LAST,
            ignoreUnresolved = true
        )
        assertFalse(snapshot.resolveProperty)
        assertEquals(ResolveMultiMode.LAST, snapshot.resolveMulti)
        assertTrue(snapshot.ignoreUnresolved)
    }

    @Test
    fun testCopy() {
        val original = DirectiveSnapshot(
            resolveProperty = false,
            resolveMulti = ResolveMultiMode.LONGEST,
            ignoreUnresolved = true
        )
        val copy = original.copy()
        assertEquals(original, copy)
    }

    @Test
    fun testSnapshotExtension() {
        val state = DirectiveState()
        state.resolveProperty = false
        state.resolveMulti = ResolveMultiMode.LAST
        state.ignoreUnresolved = true

        val snapshot = state.snapshot()

        assertFalse(snapshot.resolveProperty)
        assertEquals(ResolveMultiMode.LAST, snapshot.resolveMulti)
        assertTrue(snapshot.ignoreUnresolved)
    }

    @Test
    fun testEquality() {
        val snapshot1 = DirectiveSnapshot(
            resolveProperty = false,
            resolveMulti = ResolveMultiMode.LAST,
            ignoreUnresolved = true
        )
        val snapshot2 = DirectiveSnapshot(
            resolveProperty = false,
            resolveMulti = ResolveMultiMode.LAST,
            ignoreUnresolved = true
        )
        assertEquals(snapshot1, snapshot2)
    }

    @Test
    fun testInequality() {
        val snapshot1 = DirectiveSnapshot(resolveProperty = true)
        val snapshot2 = DirectiveSnapshot(resolveProperty = false)
        assertNotEquals(snapshot1, snapshot2)
    }
}
