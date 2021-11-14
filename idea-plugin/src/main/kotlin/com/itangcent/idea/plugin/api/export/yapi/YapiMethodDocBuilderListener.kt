package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Param
import com.itangcent.idea.plugin.api.export.condition.ConditionOnChannel
import com.itangcent.idea.plugin.api.export.core.ExportContext
import com.itangcent.idea.plugin.api.export.core.MethodDocBuilderListener
import com.itangcent.idea.plugin.api.export.core.MethodExportContext
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.config.rule.computer
import org.apache.commons.lang3.StringUtils

@ConditionOnChannel("yap")
class YapiMethodDocBuilderListener : MethodDocBuilderListener {

    @Inject
    private lateinit var ruleComputer: RuleComputer

    @Inject
    private lateinit var configReader: ConfigReader

    override fun setName(exportContext: ExportContext, methodDoc: MethodDoc, name: String) {
        //NOP
    }

    override fun appendDesc(exportContext: ExportContext, methodDoc: MethodDoc, desc: String?) {
        //NOP
    }

    override fun addParam(exportContext: ExportContext, methodDoc: MethodDoc, param: Param) {
        //NOP
    }

    override fun setRet(exportContext: ExportContext, methodDoc: MethodDoc, ret: Any?) {
        //NOP
    }

    override fun appendRetDesc(exportContext: ExportContext, methodDoc: MethodDoc, retDesc: String?) {
        //NOP
    }

    override fun startProcessMethod(methodExportContext: MethodExportContext, methodDoc: MethodDoc) {
        //NOP
    }

    override fun processCompleted(methodExportContext: MethodExportContext, methodDoc: MethodDoc) {
        val tags = ruleComputer.computer(YapiClassExportRuleKeys.TAG, methodExportContext.element())
        if (!tags.isNullOrBlank()) {
            methodDoc.setTags(StringUtils.split(tags, configReader.first("api.tag.delimiter")?.let { it + "\n" }
                ?: ",\n")
                .map { it.trim() }
                .filter { it.isNotBlank() })
        }

        val status = ruleComputer.computer(YapiClassExportRuleKeys.STATUS, methodExportContext.element())
        methodDoc.setStatus(status)

        val open = ruleComputer.computer(YapiClassExportRuleKeys.OPEN, methodExportContext.element())
        methodDoc.setOpen(open)
    }
}