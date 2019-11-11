package com.itangcent.idea.plugin.api.export

import com.itangcent.intellij.config.AutoSearchConfigReader

class EasyApiConfigReader : AutoSearchConfigReader() {

    //    @PostConstruct
    fun init() {
        loadConfigInfo()
    }

    override fun configFileNames(): List<String> {
        return listOf(
                ".easy.api.config",
                ".easy.api.yml",
                ".easy.api.yaml"
        )
    }
}
