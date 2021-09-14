package com.itangcent.idea.utils

import com.itangcent.testFramework.ContextLightCodeInsightFixtureTestCase
import org.junit.jupiter.api.Test

/**
 * Test case for [ProjectHelper]
 */
internal class ProjectHelperTest : ContextLightCodeInsightFixtureTestCase() {

    fun testGetCurrentProject() {
        assertEquals(project, ProjectHelper.getCurrentProject(null))
    }
}