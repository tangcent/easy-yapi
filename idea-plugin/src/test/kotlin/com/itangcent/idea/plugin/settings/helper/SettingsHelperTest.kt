package com.itangcent.idea.plugin.settings.helper

import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.mock.SettingBinderAdaptor

/**
 * Test case of [*SettingsHelper]
 */
abstract class SettingsHelperTest : AdvancedContextTest() {

    internal val settings = Settings()

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }
    }
}