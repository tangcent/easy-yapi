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
        val settingBinder = actionContext.instance(SettingBinder::class)
        annotation.value.forEach { property ->
            if (settingBinder.read().getPropertyValue(property).asBool() != true) {
                return false
            }
        }
        return true
    }
}