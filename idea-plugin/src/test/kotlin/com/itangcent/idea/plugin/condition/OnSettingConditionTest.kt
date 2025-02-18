package com.itangcent.idea.plugin.condition

import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.mock.SettingBinderAdaptor
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class OnSettingConditionTest : AdvancedContextTest() {
    private val setting: Settings = Settings()

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(setting)) }
    }

    @Test
    fun matches() {
        val onSettingCondition = OnSettingCondition()
        assertFalse(onSettingCondition.matches(actionContext, GenericService::class))
        assertFalse(onSettingCondition.matches(actionContext, MethodDocService::class))
        assertFalse(onSettingCondition.matches(actionContext, GenericMethodDocService::class))

        setting.genericEnable = true
        setting.methodDocEnable = true
        assertTrue(onSettingCondition.matches(actionContext, GenericService::class))
        assertTrue(onSettingCondition.matches(actionContext, MethodDocService::class))
        assertTrue(onSettingCondition.matches(actionContext, GenericMethodDocService::class))

        setting.genericEnable = false
        setting.methodDocEnable = true
        assertFalse(onSettingCondition.matches(actionContext, GenericService::class))
        assertTrue(onSettingCondition.matches(actionContext, MethodDocService::class))
        assertFalse(onSettingCondition.matches(actionContext, GenericMethodDocService::class))

        setting.genericEnable = true
        setting.methodDocEnable = false
        assertTrue(onSettingCondition.matches(actionContext, GenericService::class))
        assertFalse(onSettingCondition.matches(actionContext, MethodDocService::class))
        assertFalse(onSettingCondition.matches(actionContext, GenericMethodDocService::class))

        setting.genericEnable = false
        setting.methodDocEnable = false
        assertFalse(onSettingCondition.matches(actionContext, GenericService::class))
        assertFalse(onSettingCondition.matches(actionContext, MethodDocService::class))
        assertFalse(onSettingCondition.matches(actionContext, GenericMethodDocService::class))
    }
}

@ConditionOnSetting("genericEnable")
class GenericService

@ConditionOnSetting("methodDocEnable")
class MethodDocService

@ConditionOnSetting("genericEnable", "methodDocEnable")
class GenericMethodDocService