package com.itangcent.idea.plugin.settings

import com.itangcent.intellij.file.BeanBinder
import com.itangcent.intellij.file.CachedBeanBinder

interface SettingBinder : BeanBinder<Settings> {
}

fun SettingBinder.lazy(): SettingBinder {
    return CachedSettingBinder(this)
}

class CachedSettingBinder(settingBinder: SettingBinder) : CachedBeanBinder<Settings>(settingBinder), SettingBinder {

}