package com.itangcent.intellij.context

/**
 * Annotation to mark properties that should be automatically cleared
 * after each action is completed (when ACTION_COMPLETED event is fired).
 *
 * Usage:
 * ```kotlin
 * @AutoClear
 * private var tempData: String? = null
 * ```
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoClear 