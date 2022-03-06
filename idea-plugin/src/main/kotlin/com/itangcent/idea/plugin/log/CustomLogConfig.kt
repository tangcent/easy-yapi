package com.itangcent.idea.plugin.log

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.helper.CommonSettingsHelper
import com.itangcent.intellij.logger.LogConfig
import java.nio.charset.Charset

@Singleton
class CustomLogConfig : LogConfig() {

    @Inject
    private lateinit var commonSettingsHelper: CommonSettingsHelper

    private val logCharset by lazy {
        commonSettingsHelper.logCharset()
    }

    override fun charset(): Charset {
        return logCharset
    }
}