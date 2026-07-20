package com.itangcent.easyapi.core.ide.action

import org.junit.Assert.*
import org.junit.Test

class ChannelQuickActionGroupLogicTest {

    @Test
    fun testActionIdPrefix() {
        assertEquals(
            "com.itangcent.easy_api.actions.channel.",
            ChannelQuickActionGroup.ACTION_ID_PREFIX
        )
    }

    @Test
    fun testActionIdPrefixWithChannelId() {
        val channelId = "postman"
        val actionId = ChannelQuickActionGroup.ACTION_ID_PREFIX + channelId
        assertEquals(
            "com.itangcent.easy_api.actions.channel.postman",
            actionId
        )
    }

    @Test
    fun testActionIdPrefixWithDifferentChannelId() {
        val channelId = "hoppscotch"
        val actionId = ChannelQuickActionGroup.ACTION_ID_PREFIX + channelId
        assertEquals(
            "com.itangcent.easy_api.actions.channel.hoppscotch",
            actionId
        )
    }
}

class ChannelActionInitActivityLogicTest {

    @Test
    fun testIsProjectActivity() {
        val activity = ChannelActionInitActivity()
        assertTrue(
            "ChannelActionInitActivity should implement ProjectActivity",
            activity is com.intellij.openapi.startup.ProjectActivity
        )
    }
}

class ExportApiActionLogicTest {

    @Test
    fun testIsAnAction() {
        val action = ExportApiAction()
        assertTrue(
            "ExportApiAction should be an AnAction",
            action is com.intellij.openapi.actionSystem.AnAction
        )
    }

    @Test
    fun testImplementsIdeaLog() {
        val action = ExportApiAction()
        assertTrue(
            "ExportApiAction should implement IdeaLog",
            action is com.itangcent.easyapi.core.logging.IdeaLog
        )
    }
}

class ScriptExecutorActionLogicTest {

    @Test
    fun testIsAnAction() {
        val action = ScriptExecutorAction()
        assertTrue(
            "ScriptExecutorAction should be an AnAction",
            action is com.intellij.openapi.actionSystem.AnAction
        )
    }
}

class OpenScriptExecutorActionLogicTest {

    @Test
    fun testIsAnAction() {
        val action = OpenScriptExecutorAction()
        assertTrue(
            "OpenScriptExecutorAction should be an AnAction",
            action is com.intellij.openapi.actionSystem.AnAction
        )
    }
}

class OpenApiDashboardActionLogicTest {

    @Test
    fun testIsAnAction() {
        val action = OpenApiDashboardAction()
        assertTrue(
            "OpenApiDashboardAction should be an AnAction",
            action is com.intellij.openapi.actionSystem.AnAction
        )
    }
}
