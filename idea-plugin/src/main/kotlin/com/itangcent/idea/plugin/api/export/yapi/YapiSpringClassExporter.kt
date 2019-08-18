package com.itangcent.idea.plugin.api.export.yapi

import com.intellij.psi.PsiMethod
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.api.export.SpringRequestClassExporter

class YapiSpringClassExporter : SpringRequestClassExporter() {
    override fun processCompleted(method: PsiMethod, request: Request) {
        super.processCompleted(method, request)

        val tags = ruleComputer!!.computer(YapiClassExportRuleKeys.TAG, method)
        if (!tags.isNullOrBlank()) {
            request.setTags(tags.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() })
        }

        val status = ruleComputer.computer(YapiClassExportRuleKeys.STATUS, method)
        request.setStatus(status)
    }
}