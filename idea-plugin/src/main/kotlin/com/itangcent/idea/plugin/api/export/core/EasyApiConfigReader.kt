package com.itangcent.idea.plugin.api.export.core

import com.itangcent.intellij.config.AutoSearchConfigReader
import com.itangcent.utils.Initializable

class EasyApiConfigReader : AutoSearchConfigReader(), Initializable {

    //    @PostConstruct
    override fun init() {
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
