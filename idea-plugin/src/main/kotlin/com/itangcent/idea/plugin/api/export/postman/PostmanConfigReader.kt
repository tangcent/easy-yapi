package com.itangcent.idea.plugin.api.export.postman

import com.itangcent.intellij.config.AutoSearchConfigReader
import com.itangcent.intellij.extend.guice.PostConstruct
import java.util.*

class PostmanConfigReader : AutoSearchConfigReader() {

//    @PostConstruct
    fun init() {
        loadConfigInfo()
    }

    override fun configFileNames(): List<String> {
        return listOf(".postman.config", ".easy.api.config")
    }
}
