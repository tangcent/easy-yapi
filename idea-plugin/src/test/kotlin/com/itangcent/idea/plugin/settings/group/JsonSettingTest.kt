package com.itangcent.idea.plugin.settings.group

import com.google.inject.Inject
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.psi.JsonOption
import com.itangcent.mock.BaseContextTest
import com.itangcent.mock.SettingBinderAdaptor
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test case of [JsonSetting]
 */
internal class JsonSettingTest : BaseContextTest() {

    private val settings = Settings()

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }
    }

    @Inject
    private lateinit var jsonSetting: JsonSetting

    @Test
    fun testDefaultJsonOption() {
        assertEquals(0, jsonSetting.defaultJsonOption())
    }

    @Test
    fun testJsonOptionForInput() {
        settings.readSetter = false
        assertEquals(JsonOption.NONE, jsonSetting.jsonOptionForInput(JsonOption.NONE))
        settings.readSetter = true
        assertEquals(JsonOption.READ_SETTER, jsonSetting.jsonOptionForInput(JsonOption.NONE))
    }

    @Test
    fun testJsonOptionForOutput() {
        settings.readGetter = false
        assertEquals(JsonOption.NONE, jsonSetting.jsonOptionForOutput(JsonOption.NONE))
        settings.readGetter = true
        assertEquals(JsonOption.READ_GETTER, jsonSetting.jsonOptionForOutput(JsonOption.NONE))

    }
}