package com.itangcent.idea.plugin.api.export.postman

import com.itangcent.intellij.config.AutoSearchConfigReader
import com.itangcent.utils.Initializable

class PostmanConfigReader : AutoSearchConfigReader(), Initializable {

    //    @PostConstruct
    override fun init() {
        loadConfigInfo()
    }

    override fun configFileNames(): List<String> {
        return listOf(
                ".postman.config",
                ".postman.yml",
                ".postman.yaml",

                ".easy.api.config",
                ".easy.api.yml",
                ".easy.api.yaml"
        )
    }
}
