package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.helper.YapiTokenChecker

@Singleton
class YapiTokenCheckerSupport : YapiTokenChecker {

    @Inject
    private lateinit var yapiApiHelper: YapiApiHelper

    override fun checkToken(token: String): Boolean {
        return yapiApiHelper.getProjectInfo(token) != null
    }
}