package com.itangcent.idea.swing

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Test case of [SafeHashHelper]
 */
internal class SafeHashHelperTest {

    @Test
    fun testHash() {
        val safeHashHelper = SafeHashHelper()

        assertNull(safeHashHelper.getBean(0))
        assertNull(safeHashHelper.getBean(100))

        val a1 = SafeHashHelperTestModel("A", 1)
        val a1Hash = safeHashHelper.hash(a1)
        assertEquals(a1Hash, safeHashHelper.hash(a1))

        val b1 = SafeHashHelperTestModel("B", 1)
        val b1Hash = safeHashHelper.hash(b1)
        val a2 = SafeHashHelperTestModel("A", 9)
        val a2Hash = safeHashHelper.hash(a2)
        val b2 = SafeHashHelperTestModel("B", 9)
        val b2Hash = safeHashHelper.hash(b2)

        assertNotEquals(a1Hash, b1Hash)
        assertNotEquals(a2Hash, b2Hash)
        assertNotEquals(a1Hash, a2Hash)
        assertNotEquals(b1Hash, b2Hash)

        assertEquals(a1, safeHashHelper.getBean(a1Hash))
        assertEquals(a2, safeHashHelper.getBean(a2Hash))
        assertEquals(b1, safeHashHelper.getBean(b1Hash))
        assertEquals(b2, safeHashHelper.getBean(b2Hash))
    }


}

class SafeHashHelperTestModel(private val value: String, private val hash: Int) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SafeHashHelperTestModel
        if (value != other.value) return false
        return true
    }

    override fun hashCode(): Int {
        return hash
    }
}