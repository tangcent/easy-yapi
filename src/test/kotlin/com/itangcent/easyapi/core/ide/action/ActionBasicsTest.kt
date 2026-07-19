package com.itangcent.easyapi.core.ide.action

import com.intellij.openapi.extensions.ExtensionPointName
import com.itangcent.easyapi.core.internal.PluginInfo.PLUGIN_ID
import com.itangcent.easyapi.format.spi.FieldFormatChannel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionExistenceTest {

    @Test
    fun testEasyApiActionClassExists() {
        val clazz = Class.forName("com.itangcent.easyapi.core.ide.action.EasyApiAction")
        assertNotNull("EasyApiAction class should exist", clazz)
    }

    @Test
    fun testFieldFormatChannelsRegistered() {
        val ep = ExtensionPointName.create<FieldFormatChannel>(
            "$PLUGIN_ID.fieldFormatChannel"
        )
        val channels = ep.extensionList
        val ids = channels.map { it.id }.toSet()
        assertTrue("JSON channel should be registered", "json" in ids)
        assertTrue("JSON5 channel should be registered", "json5" in ids)
        assertTrue("Properties channel should be registered", "properties" in ids)
        assertTrue("YAML channel should be registered", "yaml" in ids)
    }

    @Test
    fun testFieldFormatActionGroupClassExists() {
        val clazz = Class.forName("com.itangcent.easyapi.format.spi.FieldFormatActionGroup")
        assertNotNull("FieldFormatActionGroup class should exist", clazz)
    }

    @Test
    fun testOpenApiDashboardActionExists() {
        val action = OpenApiDashboardAction()
        assertNotNull("OpenApiDashboardAction should be created", action)
    }

    @Test
    fun testScriptExecutorActionExists() {
        val action = ScriptExecutorAction()
        assertNotNull("ScriptExecutorAction should be created", action)
    }

    @Test
    fun testOpenScriptExecutorActionExists() {
        val action = OpenScriptExecutorAction()
        assertNotNull("OpenScriptExecutorAction should be created", action)
    }

    @Test
    fun testApiCallActionExists() {
        val action = ApiCallAction()
        assertNotNull("ApiCallAction should be created", action)
    }

    @Test
    fun testExportApiActionExists() {
        val action = ExportApiAction()
        assertNotNull("ExportApiAction should be created", action)
    }
}
