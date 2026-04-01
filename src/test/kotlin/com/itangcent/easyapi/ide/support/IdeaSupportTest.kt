package com.itangcent.easyapi.ide.support

import org.junit.Assert.*
import org.junit.Test

class IdeaSupportTest {

    @Test
    fun testIdeVersion() {
        val version = IdeaSupport.ideVersion()
        assertNotNull(version)
        assertTrue(version.isNotEmpty())
    }

    @Test
    fun testBuildNumber() {
        val buildNumber = IdeaSupport.buildNumber()
        assertNotNull(buildNumber)
        assertTrue(buildNumber.isNotEmpty())
    }

    @Test
    fun testIsVersionAtLeast() {
        val version = IdeaSupport.ideVersion()
        val prefix = version.take(4)
        
        assertTrue(IdeaSupport.isVersionAtLeast(prefix))
    }

    @Test
    fun testIsVersionAtLeastWithInvalidPrefix() {
        assertFalse(IdeaSupport.isVersionAtLeast("9999"))
    }
}
