package com.itangcent.idea.plugin.api.export.postman

import com.itangcent.common.model.Request
import com.itangcent.common.utils.KV
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.SpringRequestClassExporter
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.jvm.element.ExplicitMethod

/**
 * 1.support rule:[com.itangcent.idea.plugin.api.export.ClassExportRuleKeys.POST_PRE_REQUEST]
 * 2.support rule:[com.itangcent.idea.plugin.api.export.ClassExportRuleKeys.POST_TEST]
 *
 * @see [https://learning.postman.com/docs/postman/launching-postman/introduction/]
 */
class PostmanSpringRequestClassExporter : SpringRequestClassExporter() {

    override fun processCompleted(method: ExplicitMethod, kv: KV<String, Any?>, request: Request) {
        super.processCompleted(method, kv, request)

        val preRequest = ruleComputer!!.computer(ClassExportRuleKeys.POST_PRE_REQUEST, method)
        if (preRequest.notNullOrBlank()) {
            request.setExt(ClassExportRuleKeys.POST_PRE_REQUEST.name(), preRequest)
        }

        val test = ruleComputer.computer(ClassExportRuleKeys.POST_TEST, method)
        if (test.notNullOrBlank()) {
            request.setExt(ClassExportRuleKeys.POST_TEST.name(), test)
        }
    }
}
