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

/**
 * Appends additional text to origin, removing lines from additional that already exist in origin.
 *
 * For each line in origin, the first identical line in additional is removed.
 * This prevents duplicate content when combining doc comments with rule-based docs.
 *
 * @param additional The additional text to append (lines already in origin are removed)
 * @param separator The separator to use between origin and remaining additional text
 * @return The combined string with duplicates removed from additional
 */
fun String?.appendWithDedup(additional: String?, separator: String = "\n"): String {
    if (this.isNullOrBlank()) return additional ?: ""
    if (additional.isNullOrBlank()) return this

    val originLines = this.lines().toMutableList()
    val additionalLines = additional.lines().toMutableList()

    val originIterator = originLines.iterator()
    while (originIterator.hasNext()) {
        val originLine = originIterator.next()
        val additionalIterator = additionalLines.iterator()
        while (additionalIterator.hasNext()) {
            if (additionalIterator.next() == originLine) {
                additionalIterator.remove()
                break
            }
        }
    }

    val mergedAdditional = additionalLines.fold(mutableListOf<String>()) { acc, line ->
        if (line.isNotBlank() || acc.isEmpty() || acc.last().isNotBlank()) {
            acc.add(line)
        }
        acc
    }
    while (mergedAdditional.isNotEmpty() && mergedAdditional.first().isBlank()) {
        mergedAdditional.removeAt(0)
    }
    val remainingAdditional = mergedAdditional.joinToString(separator)
    return if (remainingAdditional.isBlank()) {
        this
    } else {
        "$this$separator$remainingAdditional"
    }
}
