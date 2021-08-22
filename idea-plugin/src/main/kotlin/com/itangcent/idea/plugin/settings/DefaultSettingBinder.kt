package com.itangcent.idea.plugin.settings

import com.google.inject.Inject
import com.google.inject.Singleton

@Singleton
class DefaultSettingBinder : SettingBinder {

    @Inject
    private lateinit var xmlSettingBinder: XmlSettingBinder

    private val cachedSettingBinder by lazy {
        xmlSettingBinder.lazy()
    }

    override fun read(): Settings {
        return cachedSettingBinder.read()
    }

    override fun save(t: Settings?) {
        xmlSettingBinder.save(t)
        cachedSettingBinder.save(t)
    }

    override fun tryRead(): Settings? {
        return cachedSettingBinder.tryRead()
    }
}