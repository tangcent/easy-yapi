package com.itangcent.idea.plugin.api.export.yapi

import com.intellij.psi.PsiMethod
import com.itangcent.common.model.MethodDoc
import com.itangcent.idea.plugin.api.export.DefaultMethodDocClassExporter

class YapiMethodDocClassExporter : DefaultMethodDocClassExporter() {

    override fun processCompleted(method: PsiMethod, methodDoc: MethodDoc) {
        super.processCompleted(method, methodDoc)

        val tags = ruleComputer!!.computer(YapiClassExportRuleKeys.TAG, method)
        if (!tags.isNullOrBlank()) {
            methodDoc.setTags(tags.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() })
        }

        val status = ruleComputer.computer(YapiClassExportRuleKeys.STATUS, method)
        methodDoc.setStatus(status)

        val open = ruleComputer.computer(YapiClassExportRuleKeys.OPEN, method)
        methodDoc.setOpen(open)
    }
}