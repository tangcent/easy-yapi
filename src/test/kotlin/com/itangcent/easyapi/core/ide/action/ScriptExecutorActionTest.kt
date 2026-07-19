package com.itangcent.easyapi.core.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ScriptExecutorActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var action: ScriptExecutorAction

    override fun setUp() {
        super.setUp()
        action = ScriptExecutorAction()
    }

    fun testActionCreation() {
        assertNotNull("ScriptExecutorAction should be created", action)
    }

    fun testActionIsAnAction() {
        assertTrue(
            "ScriptExecutorAction should be an AnAction",
            action is com.intellij.openapi.actionSystem.AnAction
        )
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

    fun testActionPerformedReturnsEarlyWithoutProject() {
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
}
