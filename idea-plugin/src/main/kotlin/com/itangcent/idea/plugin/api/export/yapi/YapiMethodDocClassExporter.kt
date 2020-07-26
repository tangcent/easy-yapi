package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.intellij.psi.PsiMethod
import com.itangcent.common.model.MethodDoc
import com.itangcent.idea.plugin.api.export.DefaultMethodDocClassExporter
import com.itangcent.intellij.config.ConfigReader
import org.apache.commons.lang3.StringUtils

class YapiMethodDocClassExporter : DefaultMethodDocClassExporter() {

    @Inject
    private val configReader: ConfigReader? = null

    override fun processCompleted(method: PsiMethod, methodDoc: MethodDoc) {
        super.processCompleted(method, methodDoc)

        val tags = ruleComputer!!.computer(YapiClassExportRuleKeys.TAG, method)
        if (!tags.isNullOrBlank()) {
            methodDoc.setTags(StringUtils.split(tags, configReader!!.first("api.tag.delimiter") ?: ",\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() })
        }

        val status = ruleComputer.computer(YapiClassExportRuleKeys.STATUS, method)
        methodDoc.setStatus(status)

        val open = ruleComputer.computer(YapiClassExportRuleKeys.OPEN, method)
        methodDoc.setOpen(open)
    }
}