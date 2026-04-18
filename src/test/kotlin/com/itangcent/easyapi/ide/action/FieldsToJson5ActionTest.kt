package com.itangcent.easyapi.ide.action

import org.junit.Assert.*
import org.junit.Test

class FieldsToJson5ActionTest {

    @Test
    fun testActionCreation() {
        val action = FieldsToJson5Action()
        assertNotNull("FieldsToJson5Action should be created", action)
    }

    @Test
    fun testActionIsAnAction() {
        val action = FieldsToJson5Action()
        assertTrue("Should be an AnAction", action is com.intellij.openapi.actionSystem.AnAction)
    }

    @Test
    fun testActionExtendsFieldFormatAction() {
        val action = FieldsToJson5Action()
        assertTrue("Should extend FieldFormatAction", action is FieldFormatAction)
    }
}
