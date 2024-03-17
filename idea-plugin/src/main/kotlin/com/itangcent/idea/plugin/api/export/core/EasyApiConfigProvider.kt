package com.itangcent.idea.plugin.api.export.core

import com.itangcent.intellij.config.LocalFileSearchConfigProvider
import com.itangcent.order.Order
import com.itangcent.order.Ordered

@Order(Ordered.HIGHEST_PRECEDENCE)
class EasyApiConfigProvider : LocalFileSearchConfigProvider() {

    override fun configFileNames(): List<String> {
        return listOf(
            ".easy.api.config",
            ".easy.api.yml",
            ".easy.api.yaml"
        )
    }
}
