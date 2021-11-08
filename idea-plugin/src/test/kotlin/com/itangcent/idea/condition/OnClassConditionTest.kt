package com.itangcent.idea.condition

import com.itangcent.idea.condition.annotation.ConditionOnClass
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case of [OnClassCondition]
 */
internal class OnClassConditionTest : PluginContextLightCodeInsightFixtureTestCase() {

    override fun beforeBind() {
        super.beforeBind()
        loadClass("spring/RequestMapping.java")
    }

    fun testMatches() {
        val onClassCondition = OnClassCondition()
        assertTrue(onClassCondition.matches(actionContext, ConditionOnRequestMapping::class))
        assertFalse(onClassCondition.matches(actionContext, ConditionOnRestController::class))
    }

    @ConditionOnClass("org.springframework.web.bind.annotation.RequestMapping")
    class ConditionOnRequestMapping

    @ConditionOnClass("org.springframework.web.bind.annotation.RestController")
    class ConditionOnRestController
}
