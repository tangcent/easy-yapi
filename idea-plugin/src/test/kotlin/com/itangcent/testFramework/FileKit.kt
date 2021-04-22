package com.itangcent.testFramework

import java.io.File
import java.nio.file.Path

private val separator = File.separator

fun Path.sub(path: String): File {
    return File("$this/$path".r())
}

fun String.sub(path: String): String {
    return "$this/$path".r()
}

fun String.escapeBackslash(): String {
    return this.replace("\\", "\\\\")
}

/**
 * redirect to real path
 */
fun String.r(): String {
    return this.replace("/", separator)
}