package com.itangcent.idea.plugin.api.export.condition

import com.itangcent.condition.AnnotatedCondition
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.intellij.context.ActionContext

/**
 * Condition that checks for [ConditionOnChannel].
 */
class OnChannelCondition : AnnotatedCondition<ConditionOnChannel>() {

    override fun matches(actionContext: ActionContext, annotation: ConditionOnChannel): Boolean {
        val exportChannel = try {
            actionContext.instance(ExportChannel::class).channel() ?: return false
        } catch (e: Exception) {
            return false
        }
        return annotation.value.contains(exportChannel)
    }
}