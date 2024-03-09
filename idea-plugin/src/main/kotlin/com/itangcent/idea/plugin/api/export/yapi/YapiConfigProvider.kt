package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.condition.Exclusion
import com.itangcent.idea.plugin.api.export.condition.ConditionOnChannel
import com.itangcent.idea.plugin.api.export.core.EasyApiConfigProvider
import com.itangcent.intellij.config.LocalFileSearchConfigProvider
import com.itangcent.order.Order
import com.itangcent.order.Ordered

@ConditionOnChannel("yapi")
@Exclusion(EasyApiConfigProvider::class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class YapiConfigProvider : LocalFileSearchConfigProvider() {

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
