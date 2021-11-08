package com.itangcent.order

/**
 * [Order] defines the sort order for an annotated component.
 * The value is optional and represents an order value as defined in the Ordered interface.
 * Lower values have higher priority. The default value is [Ordered.DEFAULT_PRECEDENCE],
 * indicating normal priority.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Order(val order: Int = Ordered.DEFAULT_PRECEDENCE)
