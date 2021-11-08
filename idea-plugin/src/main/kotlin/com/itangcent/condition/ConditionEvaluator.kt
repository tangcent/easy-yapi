package com.itangcent.condition

import com.google.inject.ImplementedBy
import com.itangcent.intellij.context.ActionContext
import kotlin.reflect.KClass

@ImplementedBy(DefaultConditionEvaluator::class)
interface ConditionEvaluator {

    fun matches(actionContext: ActionContext, beanClass: KClass<*>): Boolean
}
