package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Singleton

@Singleton
class MemoryPostmanSettingsHelper : DefaultPostmanSettingsHelper() {

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