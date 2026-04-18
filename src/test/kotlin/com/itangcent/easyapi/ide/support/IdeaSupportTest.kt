package com.itangcent.easyapi.ide.support

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class IdeaSupportTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testIdeVersionReturnsNonEmpty() {
        val version = IdeaSupport.ideVersion()
        assertNotNull("IDE version should not be null", version)
        assertTrue("IDE version should not be empty", version.isNotEmpty())
    }

    fun testBuildNumberReturnsNonEmpty() {
        val build = IdeaSupport.buildNumber()
        assertNotNull("Build number should not be null", build)
        assertTrue("Build number should not be empty", build.isNotEmpty())
    }
}
