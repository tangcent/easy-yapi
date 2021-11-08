package com.itangcent.idea.plugin.api.export.condition

import com.itangcent.condition.AnnotatedCondition
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.intellij.context.ActionContext

/**
 * Condition that checks for [ConditionOnDoc].
 */
class OnDocCondition : AnnotatedCondition<ConditionOnDoc>() {

    override fun matches(actionContext: ActionContext, annotation: ConditionOnDoc): Boolean {
        val exportDoc = try {
            actionContext.instance(ExportDoc::class).doc() ?: return false
        } catch (e: Exception) {
            return false
        }
        return annotation.value.any { exportDoc.contains(it) }
    }
}