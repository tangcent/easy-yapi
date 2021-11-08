package com.itangcent.idea.plugin.api.export.condition

import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [OnSimpleCondition]
 */
internal class OnSimpleConditionTest : AdvancedContextTest() {

    @Test
    fun matches() {
        val onSimpleCondition = OnSimpleCondition()

        //test simple is null
        assertFalse(onSimpleCondition.matches(actionContext, ConditionOnSimpleDefault::class))
        assertFalse(onSimpleCondition.matches(actionContext, ConditionOnSimpleTrue::class))
        assertTrue(onSimpleCondition.matches(actionContext, ConditionOnNotSimpleFalse::class))

        //test simple is true
        actionContext.markAsSimple()
        assertTrue(onSimpleCondition.matches(actionContext, ConditionOnSimpleDefault::class))
        assertTrue(onSimpleCondition.matches(actionContext, ConditionOnSimpleTrue::class))
        assertFalse(onSimpleCondition.matches(actionContext, ConditionOnNotSimpleFalse::class))

        //test simple is false
        actionContext.markSimple(false)
        assertFalse(onSimpleCondition.matches(actionContext, ConditionOnSimpleDefault::class))
        assertFalse(onSimpleCondition.matches(actionContext, ConditionOnSimpleTrue::class))
        assertTrue(onSimpleCondition.matches(actionContext, ConditionOnNotSimpleFalse::class))
    }

    @ConditionOnSimple
    class ConditionOnSimpleDefault

    @ConditionOnSimple(true)
    class ConditionOnSimpleTrue

    @ConditionOnSimple(false)
    class ConditionOnNotSimpleFalse

}