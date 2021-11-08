package com.itangcent.order

import kotlin.reflect.full.findAnnotation

/**
 * Ordered is an interface that can be implemented by objects that should be orderable.
 */
interface Ordered {
    fun order(): Int

    companion object {
        const val HIGHEST_PRECEDENCE = Int.MIN_VALUE
        const val LOWEST_PRECEDENCE = Int.MAX_VALUE
        const val DEFAULT_PRECEDENCE = 0
    }
}

fun Any.order(): Int {
    //implement of [Ordered]
    if (this is Ordered) {
        return this.order()
    }

    //annotated with [Order]
    this::class.findAnnotation<Order>()?.let { return it.order }

    return 0

}