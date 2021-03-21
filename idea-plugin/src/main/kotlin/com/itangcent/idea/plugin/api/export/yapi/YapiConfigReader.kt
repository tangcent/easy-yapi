package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.intellij.config.AutoSearchConfigReader
import com.itangcent.utils.Initializable

class YapiConfigReader : AutoSearchConfigReader(), Initializable {

    //    @PostConstruct
    override fun init() {
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
