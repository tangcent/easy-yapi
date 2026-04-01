package com.itangcent.easyapi.util

/**
 * Appends two strings with a separator, handling null/blank values.
 *
 * @param separator The separator to use between strings (default: newline)
 * @return The combined string, or the non-null/non-blank string if one is null/blank
 */
fun String?.append(other: String?, separator: String = "\n"): String {
    return when {
        this.isNullOrBlank() -> other ?: ""
        other.isNullOrBlank() -> this
        else -> "$this$separator$other"
    }
}
