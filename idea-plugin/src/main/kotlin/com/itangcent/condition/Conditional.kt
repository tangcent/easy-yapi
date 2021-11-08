package com.itangcent.condition

import kotlin.reflect.KClass

/**
 * Indicates that a bean is only eligible for loaded when all specified conditions match.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Conditional(vararg val value: KClass<out Condition>)
