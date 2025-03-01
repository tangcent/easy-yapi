package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Singleton
import com.itangcent.intellij.context.AutoClear

@Singleton
class MemoryPostmanSettingsHelper : DefaultPostmanSettingsHelper() {

    @AutoClear
    private var privateToken: String? = null

    override fun getPrivateToken(dumb: Boolean): String? {
        if (privateToken != null) {
            return privateToken
        }
        return super.getPrivateToken(dumb)
    }

    fun setPrivateToken(privateToken: String?) {
        this.privateToken = privateToken
    }
}