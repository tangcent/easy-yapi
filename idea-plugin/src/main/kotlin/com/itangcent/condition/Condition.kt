package com.itangcent.condition

import com.itangcent.intellij.context.ActionContext
import kotlin.reflect.KClass

/**
 * A single condition that must be matched in order for a bean to be loaded.
 * Conditions are checked immediately before create the instance of the bean.
 */
interface Condition {

    /**
     * Determine if the condition matches.
     * @param actionContext the action context of current Action
     * @param beanClass class of the bean being checked
     * @return {@code true} if the condition matches and the bean can be loaded
     */
    fun matches(actionContext: ActionContext, beanClass: KClass<*>): Boolean
}
