package com.itangcent.easyapi.ide.action

import org.junit.Assert.*
import org.junit.Test

class FieldsToJsonActionTest {

    @Test
    fun testActionCreation() {
        val action = FieldsToJsonAction()
        assertNotNull("FieldsToJsonAction should be created", action)
    }

    @Test
    fun testActionIsAnAction() {
        val action = FieldsToJsonAction()
        assertTrue("Should be an AnAction", action is com.intellij.openapi.actionSystem.AnAction)
    }

    @Test
    fun testActionExtendsFieldFormatAction() {
        val action = FieldsToJsonAction()
        assertTrue("Should extend FieldFormatAction", action is FieldFormatAction)
    }
}
