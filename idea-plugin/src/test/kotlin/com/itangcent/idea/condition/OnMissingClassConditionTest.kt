package com.itangcent.idea.condition

import com.itangcent.idea.condition.annotation.ConditionOnMissingClass
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case of [OnMissingClassCondition]
 */
internal class OnMissingClassConditionTest : PluginContextLightCodeInsightFixtureTestCase() {

    override fun beforeBind() {
        super.beforeBind()
        loadClass("spring/RequestMapping.java")
    }

    fun testMatches() {
        val onMissingClassCondition = OnMissingClassCondition()
        assertFalse(onMissingClassCondition.matches(actionContext, ConditionOnRequestMapping::class))
        assertTrue(onMissingClassCondition.matches(actionContext, ConditionOnRestController::class))
    }

    @ConditionOnMissingClass("org.springframework.web.bind.annotation.RequestMapping")
    class ConditionOnRequestMapping

    @ConditionOnMissingClass("org.springframework.web.bind.annotation.RestController")
    class ConditionOnRestController
}