package com.itangcent.easyapi.core.psi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class SuspendMergeSortTest {

    @Test
    fun sortsAscending() = runBlocking {
        val sorted = suspendMergeSort(listOf(5, 3, 9, 1, 7, 2)) { a, b -> a.compareTo(b) }
        assertEquals(listOf(1, 2, 3, 5, 7, 9), sorted)
    }

    @Test
    fun handlesEmptyAndSingleElement() = runBlocking {
        assertEquals(emptyList<Int>(), suspendMergeSort(emptyList<Int>()) { a, b -> a.compareTo(b) })
        assertEquals(listOf(1), suspendMergeSort(listOf(1)) { a, b -> a.compareTo(b) })
    }

    @Test
    fun isStableForEqualKeys() = runBlocking {
        val items = listOf("b1" to 1, "a1" to 0, "b2" to 1, "a2" to 0, "b3" to 1)
        val sorted = suspendMergeSort(items) { x, y -> x.second.compareTo(y.second) }
        assertEquals(listOf("a1", "a2", "b1", "b2", "b3"), sorted.map { it.first })
    }

    @Test
    fun comparatorMaySuspendOnAnotherDispatcher() = runBlocking {
        val sorted = suspendMergeSort(listOf(3, 1, 2)) { a, b ->
            delay(1)
            withContext(Dispatchers.Default) { a.compareTo(b) }
        }
        assertEquals(listOf(1, 2, 3), sorted)
    }

    @Test
    fun inconsistentComparatorDoesNotThrow() = runBlocking {
        // List.sortedWith can throw "Comparison method violates its general contract"
        // when the comparator is not a total order.
        val items = (1..200).toList()
        val sorted = suspendMergeSort(items) { _, _ -> -1 }
        assertEquals(items.size, sorted.size)
        assertEquals(items.toSet(), sorted.toSet())
    }
}
