package com.itangcent.idea.plugin.api.export.condition

/**
 * Conditional that only matches when exporting to the specified channel.
 * @param value postman/yapi/markdown
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionOnChannel(vararg val value: String)
