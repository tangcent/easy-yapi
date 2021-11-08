package com.itangcent.idea.plugin.api.export.condition

/**
 * Conditional that only matches when exporting the specified type of docs.
 * @param value request/methodDoc
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionOnDoc(vararg val value:String)
