package com.itangcent.test

import kotlin.test.assertTrue


fun <T> assertContentEquals(expected: Array<T>, actual: Array<T>?) {
    assertTrue(expected.contentEquals(actual))
}