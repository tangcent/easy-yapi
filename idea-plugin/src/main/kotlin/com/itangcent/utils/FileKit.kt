package com.itangcent.utils

import java.io.File

/**
 * Returns a path string with `/` converted to the OS-specific separator character.
 * On OSes where the separator is already `/`, the original string is returned.
 */
fun String.localPath(): String {
    if (File.separatorChar != '/') {
        return this.replace('/', File.separatorChar)
    }
    return this
}