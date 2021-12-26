package com.itangcent.idea.condition

import com.intellij.openapi.project.Project
import com.itangcent.condition.AnnotatedCondition
import com.itangcent.idea.condition.annotation.ConditionOnMissingClass
import com.itangcent.idea.psi.PsiClassFinder
import com.itangcent.intellij.context.ActionContext

/**
 * Condition that checks for [ConditionOnMissingClass].
 */
class OnMissingClassCondition : AnnotatedCondition<ConditionOnMissingClass>() {

    override fun matches(actionContext: ActionContext, annotation: ConditionOnMissingClass): Boolean {
        val project = actionContext.instance(Project::class)
        return actionContext.callInReadUI {
            return@callInReadUI annotation.value.all {
                PsiClassFinder.findClass(it, project) == null
            }
        } ?: false
    }
}