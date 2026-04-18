package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class ExportApiActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testUpdateWithProject() {
        val action = ExportApiAction()
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { project }

        action.update(event)

        assertTrue("Action should be enabled with project", event.presentation.isEnabled)
    }

    fun testUpdateWithoutProject() {
        val action = ExportApiAction()
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { null }

        action.update(event)

        assertFalse("Action should be disabled without project", event.presentation.isEnabled)
    }
}
