package com.itangcent.order

import kotlin.reflect.full.findAnnotation

/**
 * Objects that implement this interface can be sorted according to their order value.
 * The order value is typically an integer that represents the relative order of the
 * object in a sequence or collection.
 */
interface Ordered {
    /**
     * Returns the order value of this object.
     */
    fun order(): Int

    companion object {
        /**
         * The highest possible order value.
         */
        const val HIGHEST_PRECEDENCE = Int.MIN_VALUE

        /**
         * The lowest possible order value.
         */
        const val LOWEST_PRECEDENCE = Int.MAX_VALUE

        /**
         * The default order value.
         */
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