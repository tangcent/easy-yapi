package com.itangcent.utils

import com.itangcent.test.assertContentEquals
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class TemplateKitTest {

    @Test
    fun resolvePlaceHolder() {
        // Test resolving a null placeholder
        val nullPlaceholder = TemplateKit.resolvePlaceHolder(null)
        assertArrayEquals(null, nullPlaceholder)

        // Test resolving a single character placeholder
        val singleCharPlaceholder = TemplateKit.resolvePlaceHolder('$')
        assertArrayEquals(charArrayOf('$'), singleCharPlaceholder)

        // Test resolving a string placeholder
        val stringPlaceholder = TemplateKit.resolvePlaceHolder("#hello")
        assertArrayEquals(charArrayOf('#', 'h', 'e', 'l', 'o'), stringPlaceholder)

        // Test resolving an array of placeholders
        val arrayPlaceholder = TemplateKit.resolvePlaceHolder(arrayOf("$", "#bc", '#', arrayOf('#', '$')))
        assertArrayEquals(charArrayOf('$', '#', 'b', 'c'), arrayPlaceholder)

        // Test resolving a collection of placeholders
        val collectionPlaceholder = TemplateKit.resolvePlaceHolder(listOf('#', "\$bc", arrayOf('#', '$')))
        assertArrayEquals(charArrayOf('#', '$', 'b', 'c'), collectionPlaceholder)

        // Test resolving a placeholder with nested collections
        val nestedCollectionPlaceholder = TemplateKit.resolvePlaceHolder(listOf('$', listOf('#', "#c"), arrayOf('#', arrayOf('$', '#'))))
        assertArrayEquals(charArrayOf('$', '#', 'c'), nestedCollectionPlaceholder)

        // Test resolving a placeholder with no characters
        val emptyPlaceholder = TemplateKit.resolvePlaceHolder("")
        assertArrayEquals(null, emptyPlaceholder)
    }
}