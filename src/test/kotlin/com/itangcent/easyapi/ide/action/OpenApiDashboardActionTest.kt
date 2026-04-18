package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class OpenApiDashboardActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testActionPerformedWithProject() {
        val action = OpenApiDashboardAction()
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { project }

        action.actionPerformed(event)
    }

    fun testActionPerformedWithoutProject() {
        val action = OpenApiDashboardAction()
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { null }

        action.actionPerformed(event)
    }
}
