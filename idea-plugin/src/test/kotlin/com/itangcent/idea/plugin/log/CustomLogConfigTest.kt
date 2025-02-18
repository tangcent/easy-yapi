package com.itangcent.idea.plugin.log

import com.google.inject.Inject
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.LogConfig
import com.itangcent.mock.BaseContextTest
import com.itangcent.mock.SettingBinderAdaptor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Test case for [CustomLogConfig]
 *
 * @author tangcent
 */
internal class CustomLogConfigTest : BaseContextTest() {

    private val settings = Settings()

    @Inject
    private lateinit var logConfig: LogConfig

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(LogConfig::class) { it.with(CustomLogConfig::class) }
        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(settings))
        }
    }

    @Test
    fun charsetByDefault() {
        Assertions.assertEquals(Charsets.UTF_8, logConfig.charset())
    }

    @ParameterizedTest
    @ValueSource(strings = ["UTF-8", "UTF-16", "GBK"])
    fun charset(charset: String) {
        settings.logCharset = charset
        Assertions.assertEquals(charset, logConfig.charset().displayName())
    }
}