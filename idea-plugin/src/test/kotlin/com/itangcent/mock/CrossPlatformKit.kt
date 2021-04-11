package com.itangcent.mock

fun String.toUnixString(): String {
    return this.replace(LINE_SEPARATOR, "\n")
}

private val LINE_SEPARATOR = System.getProperty("line.separator")