package com.itangcent.utils

import com.itangcent.test.assertContentEquals
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class TemplateKitTest {

    @Test
    fun resolvePlaceHolder() {
        assertNull(TemplateKit.resolvePlaceHolder(null))
        assertNull(TemplateKit.resolvePlaceHolder(""))
        assertContentEquals(arrayOf('$'), TemplateKit.resolvePlaceHolder("$"))
        assertContentEquals(arrayOf('$', '#'), TemplateKit.resolvePlaceHolder("$#"))
        assertContentEquals(arrayOf('$'), TemplateKit.resolvePlaceHolder(arrayOf("$")))
        assertContentEquals(arrayOf('$', '#'), TemplateKit.resolvePlaceHolder(arrayOf("$#")))
        assertContentEquals(arrayOf('$'), TemplateKit.resolvePlaceHolder(arrayOf('$')))
        assertContentEquals(arrayOf('$', '#'), TemplateKit.resolvePlaceHolder(arrayOf("$", '#')))
        assertContentEquals(arrayOf('$', '#'), TemplateKit.resolvePlaceHolder(arrayOf("$", "#")))
        assertContentEquals(arrayOf('$'), TemplateKit.resolvePlaceHolder(listOf('$')))
        assertContentEquals(arrayOf('$', '#'), TemplateKit.resolvePlaceHolder(listOf("$", '#')))
        assertContentEquals(arrayOf('$', '#'), TemplateKit.resolvePlaceHolder(listOf("$", "#")))
    }
}