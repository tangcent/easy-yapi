package com.itangcent.test

import kotlin.test.assertEquals
import kotlin.test.assertTrue


fun <T> assertContentEquals(expected: Array<T>, actual: Array<T>?) {
    assertTrue(expected.contentEquals(actual))
}

fun assertLinesEqualsIgnoreOrder(expected: String, actual: String) {
    val expectedLines = expected.lines()
    val actualLines = actual.lines()
    assertEquals(expectedLines.size, actualLines.size)
    assertEquals(expectedLines.sorted(), actualLines.sorted())
}

fun assertLinesContain(expected: String, actual: String) {
    val expectedLines = expected.lines()
    val actualLines = actual.lines().toHashSet()
    assertTrue(actualLines.containsAll(expectedLines))
}