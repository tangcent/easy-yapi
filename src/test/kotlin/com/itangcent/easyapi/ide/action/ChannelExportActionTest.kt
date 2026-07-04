package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ChannelExportActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testToString() {
        val action = ChannelExportAction("postman", "Postman")
        assertEquals("Export to Postman", action.toString())
    }

    fun testToStringWithDifferentChannel() {
        val action = ChannelExportAction("hoppscotch", "Hoppscotch")
        assertEquals("Export to Hoppscotch", action.toString())
    }

    fun testActionUpdateThread() {
        val action = ChannelExportAction("test", "Test")
        assertEquals(
            com.intellij.openapi.actionSystem.ActionUpdateThread.BGT,
            action.actionUpdateThread
        )
    }

    fun testUpdateWithProject() {
        val action = ChannelExportAction("test", "Test")
        val presentation = Presentation()
        val event = AnActionEvent.createEvent(DataContext { project }, presentation, "test", ActionUiKind.NONE, null)

        action.update(event)

        assertTrue("Action should be enabled with project", event.presentation.isEnabled)
    }

    fun testUpdateWithoutProject() {
        val action = ChannelExportAction("test", "Test")
        val presentation = Presentation()
        val event = AnActionEvent.createEvent(DataContext { null }, presentation, "test", ActionUiKind.NONE, null)

        action.update(event)

        assertFalse("Action should be disabled without project", event.presentation.isEnabled)
    }

    fun testActionPerformedReturnsEarlyWithoutProject() {
        val action = ChannelExportAction("test", "Test")
        val presentation = Presentation()
        val event = AnActionEvent.createEvent(DataContext { null }, presentation, "test", ActionUiKind.NONE, null)

        // Should not throw
        action.actionPerformed(event)
    }

    fun testIsAnAction() {
        val action = ChannelExportAction("test", "Test")
        assertTrue(
            "ChannelExportAction should be an AnAction",
            action is com.intellij.openapi.actionSystem.AnAction
        )
    }
}
