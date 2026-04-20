package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
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
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { null }

        action.actionPerformed(event)
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
