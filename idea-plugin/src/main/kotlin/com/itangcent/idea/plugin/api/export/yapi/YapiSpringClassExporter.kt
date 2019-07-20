package com.itangcent.idea.plugin.api.export.yapi

import com.intellij.psi.PsiMethod
import com.itangcent.common.exporter.ParseHandle
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.api.export.SpringClassExporter

class YapiSpringClassExporter : SpringClassExporter() {

    override fun processCompleted(method: PsiMethod, request: Request, parseHandle: ParseHandle) {
        super.processCompleted(method, request, parseHandle)

        val tags = ruleComputer!!.computer(YapiClassExportRuleKeys.TAG, method)
        if (!tags.isNullOrBlank()) {
            request.setExt("tags", tags.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() })
        }
    }
}