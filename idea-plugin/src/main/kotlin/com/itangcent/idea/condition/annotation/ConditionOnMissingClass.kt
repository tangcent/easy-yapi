package com.itangcent.idea.condition.annotation

/**
 * Conditional that only matches when the specified classes not be found in current project.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionOnMissingClass(vararg val value: String)