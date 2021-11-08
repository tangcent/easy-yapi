package com.itangcent.idea.plugin.api.export.condition

import com.itangcent.condition.AnnotatedCondition
import com.itangcent.intellij.context.ActionContext

/**
 * Condition that checks for [ConditionOnSimple].
 */
class OnSimpleCondition : AnnotatedCondition<ConditionOnSimple>() {

    override fun matches(actionContext: ActionContext, annotation: ConditionOnSimple): Boolean {
        return actionContext.isSimple() == annotation.value
    }
}