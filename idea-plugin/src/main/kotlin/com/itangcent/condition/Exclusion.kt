package com.itangcent.condition

import kotlin.reflect.KClass

/**
 * If current class be loaded, Exclude specific classes such that they will never be loaded.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Exclusion(vararg val value: KClass<*>)
