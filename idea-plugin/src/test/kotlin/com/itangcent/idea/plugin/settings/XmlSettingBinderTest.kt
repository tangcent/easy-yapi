package com.itangcent.idea.plugin.settings

import com.google.inject.Inject
import com.intellij.openapi.components.ServiceManager
import com.itangcent.idea.plugin.settings.xml.ApplicationSettingsComponent
import com.itangcent.idea.plugin.settings.xml.ProjectSettingsComponent
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import kotlin.test.assertNotEquals

/**
 * Test case of [XmlSettingBinder]
 */
internal class XmlSettingBinderTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var settingBinder: SettingBinder

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SettingBinder::class) { it.with(XmlSettingBinder::class) }
    }

    fun testReadAndSave() {

        assertNull(settingBinder.tryRead())
        val settings = settingBinder.read()
        assertEquals(Settings(), settings)

        //assert that change on settings will not be stored
        settings.readSetter = !settings.readSetter
        assertEquals(Settings(), settingBinder.read())
        assertNotEquals(settings, settingBinder.read())

        settingBinder.save(settings)
        assertNotEquals(Settings(), settingBinder.read())
        assertEquals(settings, settingBinder.read())
        assertEquals(settings, settingBinder.tryRead())

        settingBinder.save(null)
        assertNull(settingBinder.tryRead())

    }
}