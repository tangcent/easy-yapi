package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.intellij.config.AutoSearchConfigReader
import com.itangcent.intellij.extend.guice.PostConstruct
import java.util.*

class YapiConfigReader : AutoSearchConfigReader() {

    @PostConstruct
    fun init() {
        loadConfigInfo()
    }

    override fun configFileNames(): List<String> {
        return Arrays.asList(".yapi.config", ".easy.api.config")
    }
}
