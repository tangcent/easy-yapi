package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class ExportApiActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var action: ExportApiAction

    override fun setUp() {
        super.setUp()
        action = ExportApiAction()
    }

    fun testActionIsAnAction() {
        assertTrue(
            "ExportApiAction should be an AnAction",
            action is com.intellij.openapi.actionSystem.AnAction
        )
    }

    fun testActionImplementsIdeaLog() {
        assertTrue(
            "ExportApiAction should implement IdeaLog",
            action is IdeaLog
        )
    }

    fun testUpdateWithProject() {
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { project }

        action.update(event)

        assertTrue("Action should be enabled with project", event.presentation.isEnabled)
    }

    fun testUpdateWithoutProject() {
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { null }

        action.update(event)

        assertFalse("Action should be disabled without project", event.presentation.isEnabled)
    }

    fun testActionPerformedReturnsEarlyWithoutProject() {
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { null }

        action.actionPerformed(event)
    }
}
