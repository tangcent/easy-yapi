package com.itangcent.easyapi.logging

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class IdeaConsoleProviderTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testGetConsoleReturnsNonNull() {
        val provider = IdeaConsoleProvider.getInstance(project)
        val console = provider.getConsole()
        assertNotNull("getConsole should return non-null", console)
    }

    fun testGetConsoleReturnsSameInstance() {
        val provider = IdeaConsoleProvider.getInstance(project)
        val console1 = provider.getConsole()
        val console2 = provider.getConsole()
        assertSame("getConsole should return same instance", console1, console2)
    }

    fun testGetConsoleReturnsIdeaConsole() {
        val provider = IdeaConsoleProvider.getInstance(project)
        val console = provider.getConsole()
        assertTrue("Console should implement IdeaConsole", console is IdeaConsole)
    }

    fun testGetInstanceReturnsSameProvider() {
        val provider1 = IdeaConsoleProvider.getInstance(project)
        val provider2 = IdeaConsoleProvider.getInstance(project)
        assertSame("getInstance should return same provider", provider1, provider2)
    }
}
