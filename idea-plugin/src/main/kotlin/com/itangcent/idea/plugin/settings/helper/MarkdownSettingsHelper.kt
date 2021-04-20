package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.MarkdownFormatType
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.utils.Charsets
import java.nio.charset.Charset

@Singleton
class MarkdownSettingsHelper {

    @Inject
    private lateinit var settingBinder: SettingBinder

    fun outputCharset(): Charset {
        return Charsets.forName(settingBinder.read().outputCharset)?.charset() ?: kotlin.text.Charsets.UTF_8
    }

    fun outputDemo(): Boolean {
        return settingBinder.read().outputDemo
    }

    fun markdownFormatType(): MarkdownFormatType {
        return MarkdownFormatType.valueOf(settingBinder.read().markdownFormatType)
    }
}