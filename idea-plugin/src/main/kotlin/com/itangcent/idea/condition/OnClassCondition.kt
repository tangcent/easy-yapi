package com.itangcent.idea.condition

import com.intellij.openapi.project.Project
import com.itangcent.condition.AnnotatedCondition
import com.itangcent.idea.condition.annotation.ConditionOnClass
import com.itangcent.idea.psi.PsiClassFinder
import com.itangcent.intellij.context.ActionContext

/**
 * Condition that checks for [ConditionOnClass].
 */
class OnClassCondition : AnnotatedCondition<ConditionOnClass>() {

    override fun matches(actionContext: ActionContext, annotation: ConditionOnClass): Boolean {
        val project = actionContext.instance(Project::class)
        return annotation.value.all {
            PsiClassFinder.findClass(it, project) != null
        }
    }
}