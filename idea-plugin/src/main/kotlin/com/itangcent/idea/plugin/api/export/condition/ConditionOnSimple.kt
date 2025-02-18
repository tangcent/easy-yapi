package com.itangcent.idea.plugin.api.export.condition

import com.itangcent.idea.plugin.api.export.condition.ConditionOnSimple.Companion.CACHE_NAME
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder

/**
 * Conditional that only matches when the [CACHE_NAME] cached in the actionContext equals the given [value].
 * @param value true/false
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionOnSimple(val value: Boolean = true) {
    companion object {
        const val CACHE_NAME = "is_simple"
    }
}

fun ActionContext.markAsSimple() {
    this.cache(CACHE_NAME, true)
}

fun ActionContextBuilder.markAsSimple() {
    this.cache(CACHE_NAME, true)
}

fun ActionContext.markSimple(value: Boolean) {
    this.cache(CACHE_NAME, value)
}

fun ActionContextBuilder.markSimple(value: Boolean) {
    this.cache(CACHE_NAME, value)
}

fun ActionContext.isSimple(): Boolean {
    return this.getCache<Boolean>(CACHE_NAME) ?: false
}