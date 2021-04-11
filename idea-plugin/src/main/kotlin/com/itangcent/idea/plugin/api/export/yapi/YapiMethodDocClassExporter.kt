package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.itangcent.common.model.MethodDoc
import com.itangcent.idea.plugin.api.export.DefaultMethodDocClassExporter
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.jvm.element.ExplicitMethod
import org.apache.commons.lang3.StringUtils

class YapiMethodDocClassExporter : DefaultMethodDocClassExporter() {

    @Inject
    private val configReader: ConfigReader? = null

    override fun processCompleted(method: ExplicitMethod, methodDoc: MethodDoc) {
        super.processCompleted(method, methodDoc)

        val tags = ruleComputer!!.computer(YapiClassExportRuleKeys.TAG, method)
        if (!tags.isNullOrBlank()) {
            methodDoc.setTags(StringUtils.split(tags, configReader!!.first("api.tag.delimiter")?.let { it + "\n" }
                    ?: ",\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() })
        }

        val status = ruleComputer.computer(YapiClassExportRuleKeys.STATUS, method)
        methodDoc.setStatus(status)

        val open = ruleComputer.computer(YapiClassExportRuleKeys.OPEN, method)
        methodDoc.setOpen(open)
    }
}