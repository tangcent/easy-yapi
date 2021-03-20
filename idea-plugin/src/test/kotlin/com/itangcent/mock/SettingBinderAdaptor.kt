package com.itangcent.mock

import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings

@Singleton
class SettingBinderAdaptor : SettingBinder {

    private var settings: Settings

    constructor(settings: Settings) {
        this.settings = settings
    }

    constructor() {
        this.settings = Settings()
    }

    override fun read(): Settings {
        return settings
    }

    override fun save(t: Settings?) {
        if (t != null) {
            this.settings = t
        }
    }

    override fun tryRead(): Settings? {
        return settings
    }
}