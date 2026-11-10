package com.itangcent.easyapi.core.psi

/**
 * Stable merge sort with a suspending comparator.
 *
 * The comparator may suspend (for example, while evaluating a rule script)
 * without blocking the calling thread or holding the read lock for the whole
 * sort. Unlike List.sortedWith, an inconsistent comparator does not throw
 * "Comparison method violates its general contract".
 */
internal suspend fun <T> suspendMergeSort(
    items: List<T>,
    comparator: suspend (T, T) -> Int
): List<T> {
    if (items.size <= 1) return items
    val mid = items.size / 2
    val left = suspendMergeSort(items.subList(0, mid), comparator)
    val right = suspendMergeSort(items.subList(mid, items.size), comparator)
    val merged = ArrayList<T>(items.size)
    var i = 0
    var j = 0
    while (i < left.size && j < right.size) {
        if (comparator(left[i], right[j]) <= 0) merged.add(left[i++]) else merged.add(right[j++])
    }
    while (i < left.size) merged.add(left[i++])
    while (j < right.size) merged.add(right[j++])
    return merged
}
