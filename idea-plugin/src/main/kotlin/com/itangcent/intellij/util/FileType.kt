package com.itangcent.intellij.util

/**
 * All file types currently supported.
 */
enum class FileType(private val suffix: String) {

    JAVA("java"),
    KOTLIN("kt"),
    SCALA("scala");

    fun suffix(): String {
        return suffix
    }

    companion object {
        fun acceptable(fileName: String): Boolean {
            return FileType.values().any { fileName.endsWith(it.suffix()) }
        }
    }
}