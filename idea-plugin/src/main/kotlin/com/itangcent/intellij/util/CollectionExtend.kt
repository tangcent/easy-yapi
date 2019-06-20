package com.itangcent.intellij.util

/**
 * Accumulates value starting with the first element and applying [operation] from left to right to current accumulator value and each element.
 */
public inline fun <S, T : S> Iterable<T>.reduceSafely(operation: (acc: S, T) -> S): S? {
    val iterator = this.iterator()
    if (!iterator.hasNext()) return null
    var accumulator: S = iterator.next()
    while (iterator.hasNext()) {
        accumulator = operation(accumulator, iterator.next())
    }
    return accumulator
}
