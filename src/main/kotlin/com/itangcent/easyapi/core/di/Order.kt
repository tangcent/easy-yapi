package com.itangcent.easyapi.core.di

/**
 * Annotation for specifying the order of a component.
 *
 * Components with lower order values are processed before those with higher values.
 * This is used for ordering SPI-loaded implementations.
 *
 * @param value The order value (lower values = higher priority)
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Order(val value: Int)
