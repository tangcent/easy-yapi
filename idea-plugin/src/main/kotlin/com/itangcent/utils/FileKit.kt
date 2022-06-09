package com.itangcent.utils

import java.io.File

fun String.localPath(): String {
    if (File.separatorChar != '/') {
        return this.replace('/', File.separatorChar)
    }
    return this
}