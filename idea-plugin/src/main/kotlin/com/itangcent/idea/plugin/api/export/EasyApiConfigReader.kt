package com.itangcent.idea.plugin.api.export

import com.itangcent.intellij.config.AutoSearchConfigReader
import com.itangcent.intellij.extend.guice.PostConstruct
import java.util.*

class EasyApiConfigReader : AutoSearchConfigReader() {

    @PostConstruct
    fun init() {
        loadConfigInfo()
    }

    override fun configFileNames(): List<String> {
        return listOf(".easy.api.config")
    }
}
