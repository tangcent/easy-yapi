package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
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
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { project }

        action.actionPerformed(event)
    }

    fun testActionPerformedWithoutProject() {
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { null }

        action.actionPerformed(event)
    }

    fun testActionPerformedDoesNotThrowWithProject() {
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { project }

        try {
            action.actionPerformed(event)
        } catch (e: Exception) {
            fail("actionPerformed should not throw with project: ${e.message}")
        }
    }
}
