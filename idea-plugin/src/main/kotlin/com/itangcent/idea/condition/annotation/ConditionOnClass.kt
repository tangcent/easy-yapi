package com.itangcent.idea.condition.annotation

/**
 * Conditional that only matches when the specified classes be found in current project.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionOnClass(vararg val value: String)