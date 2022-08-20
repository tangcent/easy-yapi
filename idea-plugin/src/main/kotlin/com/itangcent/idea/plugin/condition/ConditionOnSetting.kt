package com.itangcent.idea.plugin.condition

import com.itangcent.idea.plugin.settings.Settings

/**
 * Conditional that only matches when the special property in the [Settings] is true.
 * @param value properties
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionOnSetting(vararg val value: String, val havingValue: String = "")
