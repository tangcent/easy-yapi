package com.itangcent.easyapi.settings

import com.intellij.util.messages.Topic

interface SettingsChangeListener {

    fun settingsChanged()

    companion object {
        val TOPIC: Topic<SettingsChangeListener> = Topic.create(
            "EasyApi Settings Changed",
            SettingsChangeListener::class.java
        )
    }
}
