package com.itangcent.condition

import com.itangcent.intellij.context.ActionContext
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.reflect.KClass
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [DefaultConditionEvaluator]
 */
internal class DefaultConditionEvaluatorTest {

    @Test
    fun matches() {
        val actionContext = mock<ActionContext>()
        MyCondition.matches = false
        assertFalse(DefaultConditionEvaluator().matches(actionContext, FakeBeanService::class))
        MyCondition.matches = true
        assertTrue(DefaultConditionEvaluator().matches(actionContext, FakeBeanService::class))
    }
}

@Conditional(MyCondition::class)
class FakeBeanService {

}

class MyCondition : Condition {
    override fun matches(actionContext: ActionContext, beanClass: KClass<*>): Boolean {
        return matches
    }

    companion object {
        var matches = false
    }
}