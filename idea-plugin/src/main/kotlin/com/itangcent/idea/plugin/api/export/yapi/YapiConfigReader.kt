package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.intellij.config.AutoSearchConfigReader

class YapiConfigReader : AutoSearchConfigReader() {

    //    @PostConstruct
    fun init() {
        loadConfigInfo()
    }

    override fun configFileNames(): List<String> {
        return listOf(
                ".yapi.config",
                ".yapi.yml",
                ".yapi.yaml",

                ".easy.api.config",
                ".easy.api.yml",
                ".easy.api.yaml"
        )
    }
}
