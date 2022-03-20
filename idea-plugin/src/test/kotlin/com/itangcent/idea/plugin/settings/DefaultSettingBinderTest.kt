package com.itangcent.idea.plugin.settings

import com.google.inject.Inject
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertNotEquals

/**
 * Test case of [DefaultSettingBinder]
 */
internal class DefaultSettingBinderTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var settingBinder: SettingBinder

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SettingBinder::class) { it.with(DefaultSettingBinder::class) }
    }

    fun testReadAndSave() {

        settingBinder.save(null)
        settingBinder.tryRead().let {
            assertTrue(it == null || it == Settings())
        }
        val settings = settingBinder.read()
        assertEquals(Settings(), settings)

        //assert that change on settings will not be stored
        //but settingBinder read will return the same cache instance
        settings.readSetter = !settings.readSetter
        assertNotEquals(Settings(), settingBinder.read())
        assertEquals(settings, settingBinder.read())

        settingBinder.save(settings)
        assertNotEquals(Settings(), settingBinder.read())
        assertEquals(settings, settingBinder.read())
        assertEquals(settings, settingBinder.tryRead())

        assertDoesNotThrow { settingBinder.save(null) }
    }
}