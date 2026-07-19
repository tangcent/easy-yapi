package com.itangcent.easyapi.core.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class OpenApiDashboardActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var action: OpenApiDashboardAction

    override fun setUp() {
        super.setUp()
        action = OpenApiDashboardAction()
    }

    fun testActionIsAnAction() {
        assertTrue(
            "OpenApiDashboardAction should be an AnAction",
            action is com.intellij.openapi.actionSystem.AnAction
        )
    }

    fun testActionPerformedWithProject() {
        val presentation = Presentation()
        val event = AnActionEvent.createEvent(
            DataContext { project },
            presentation,
            "test",
            ActionUiKind.NONE,
            null
        )

        action.actionPerformed(event)
    }

    fun testActionPerformedWithoutProject() {
        val presentation = Presentation()
        val event = AnActionEvent.createEvent(
            DataContext { null },
            presentation,
            "test",
            ActionUiKind.NONE,
            null
        )

        action.actionPerformed(event)
    }

    fun testActionPerformedDoesNotThrowWithProject() {
        val presentation = Presentation()
        val event = AnActionEvent.createEvent(
            DataContext { project },
            presentation,
            "test",
            ActionUiKind.NONE,
            null
        )

        try {
            action.actionPerformed(event)
        } catch (e: Exception) {
            fail("actionPerformed should not throw with project: ${e.message}")
        }
    }
}
