package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ApiCallActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testActionIsAnAction() {
        val action = ApiCallAction()
        assertTrue("Should be an AnAction", action is com.intellij.openapi.actionSystem.AnAction)
    }

    fun testActionExtendsEasyApiAction() {
        val action = ApiCallAction()
        assertTrue("Should extend EasyApiAction", action is EasyApiAction)
    }

    fun testUpdateWithNoProject() {
        val action = ApiCallAction()
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { null }

        action.update(event)

        assertFalse("Action should not be visible without file", event.presentation.isEnabledAndVisible)
    }
}
