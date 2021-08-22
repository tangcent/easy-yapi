package com.itangcent.idea.plugin.settings

import com.google.inject.ImplementedBy
import com.itangcent.intellij.file.BeanBinder
import com.itangcent.intellij.file.CachedBeanBinder

@ImplementedBy(DefaultSettingBinder::class)
interface SettingBinder : BeanBinder<Settings> {
}

fun SettingBinder.update(updater: (Settings) -> Unit) {
    this.read().also(updater).let { this.save(it) }
}

fun SettingBinder.lazy(): SettingBinder {
    return CachedSettingBinder(this)
}

class CachedSettingBinder(settingBinder: SettingBinder) : CachedBeanBinder<Settings>(settingBinder), SettingBinder {

}