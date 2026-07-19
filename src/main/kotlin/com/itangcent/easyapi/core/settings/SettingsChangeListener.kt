package com.itangcent.easyapi.core.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
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

fun Project.onSettingsChanged(disposable: Disposable, handler: () -> Unit) {
    messageBus.connect(disposable).subscribe(SettingsChangeListener.TOPIC, object : SettingsChangeListener {
        override fun settingsChanged() {
            handler()
        }
    })
}

fun Project.onSettingsChanged(handler: () -> Unit) {
    messageBus.connect().subscribe(SettingsChangeListener.TOPIC, object : SettingsChangeListener {
        override fun settingsChanged() {
            handler()
        }
    })
}