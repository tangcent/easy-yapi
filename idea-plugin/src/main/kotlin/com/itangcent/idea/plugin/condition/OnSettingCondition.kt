package com.itangcent.idea.plugin.condition

import com.itangcent.common.utils.asBool
import com.itangcent.common.utils.getPropertyValue
import com.itangcent.condition.AnnotatedCondition
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.context.ActionContext

/**
 * Condition that checks for [ConditionOnSetting].
 */
class OnSettingCondition : AnnotatedCondition<ConditionOnSetting>() {

    override fun matches(actionContext: ActionContext, annotation: ConditionOnSetting): Boolean {
        val valueChecker = getValueChecker(annotation.havingValue)
        val settingBinder = actionContext.instance(SettingBinder::class)
        annotation.value.forEach { property ->
            if (!valueChecker(settingBinder.read().getPropertyValue(property))) {
                return false
            }
        }
        return true
    }

    private fun getValueChecker(havingValue: String): (Any?) -> Boolean {
        if (havingValue == "") {
            return { it.asBool() == true }
        }
        return { it?.toString() == havingValue }

    }
}