package com.itangcent.easyapi.core.di

/**
 * Interface for components that have an order value.
 *
 * Implementations can be sorted by their order value.
 * Lower values indicate higher priority.
 */
interface Ordered {
    /**
     * The order value for this component.
     */
    val order: Int
}

/**
 * Retrieves the order value for any object.
 *
 * If the object implements [Ordered], returns its order property.
 * Otherwise, checks for the [Order] annotation.
 * Returns 0 if no order is specified.
 *
 * @return The order value
 */
fun Any.order(): Int {
    if (this is Ordered) return this.order
    return this::class.java.getAnnotation(Order::class.java)?.value ?: 0
}
