package com.itangcent.easyapi.util

/**
 * Supported source file types for API parsing.
 *
 * The plugin can parse API endpoints from Java, Kotlin, and Groovy files.
 *
 * @see acceptable for checking if a file is supported
 */
enum class FileType(private val suffix: String) {
    /** Java source files (.java) */
    JAVA("java"),
    /** Kotlin source files (.kt) */
    KOTLIN("kt"),
    /** Groovy source files (.groovy) */
    GROOVY("groovy");

    /**
     * Returns the file suffix (without the dot).
     */
    fun suffix(): String = suffix

    companion object {
        /**
         * Checks if the given file name is a supported source file.
         *
         * @param fileName The file name to check
         * @return true if the file is a Java, Kotlin, or Groovy file
         */
        fun acceptable(fileName: String): Boolean {
            return values().any { fileName.endsWith(it.suffix()) }
        }
    }
}
