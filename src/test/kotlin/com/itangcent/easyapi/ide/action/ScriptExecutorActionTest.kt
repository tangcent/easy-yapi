package com.itangcent.easyapi.ide.action

import org.junit.Assert.*
import org.junit.Test

class ScriptExecutorActionTest {

    @Test
    fun testActionCreation() {
        val action = ScriptExecutorAction()
        assertNotNull("ScriptExecutorAction should be created", action)
    }

    @Test
    fun testActionIsAnAction() {
        val action = ScriptExecutorAction()
        assertTrue("Should be an AnAction", action is com.intellij.openapi.actionSystem.AnAction)
    }
}
