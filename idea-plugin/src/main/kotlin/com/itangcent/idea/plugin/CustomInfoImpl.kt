package com.itangcent.idea.plugin

import com.itangcent.intellij.CustomInfo

class CustomInfoImpl : CustomInfo {
    override fun pluginName(): String {
        return "easy-yapi"
    }
}