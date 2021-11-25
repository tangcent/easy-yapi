package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.itangcent.common.model.*
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.plugin.api.export.condition.ConditionOnChannel
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.core.ExportContext
import com.itangcent.idea.plugin.api.export.core.MethodExportContext
import com.itangcent.idea.plugin.api.export.core.RequestBuilderListener
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.config.rule.computer

/**
 * 1.support rule:[com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys.POST_PRE_REQUEST]
 * 2.support rule:[com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys.POST_TEST]
 *
 * @see [https://learning.postman.com/docs/writing-scripts/intro-to-scripts/]
 */
@ConditionOnChannel("postman")
class PostmanRequestBuilderListener : RequestBuilderListener {

    @Inject
    protected lateinit var ruleComputer: RuleComputer

    override fun setName(exportContext: ExportContext, request: Request, name: String) {
        //NOP
    }

    override fun setMethod(exportContext: ExportContext, request: Request, method: String) {
        //NOP
    }

    override fun setPath(exportContext: ExportContext, request: Request, path: URL) {
        //NOP
    }

    override fun setModelAsBody(exportContext: ExportContext, request: Request, model: Any) {
        //NOP
    }

    override fun addModelAsParam(exportContext: ExportContext, request: Request, model: Any) {
        //NOP
    }

    override fun addFormParam(exportContext: ExportContext, request: Request, formParam: FormParam) {
        //NOP
    }

    override fun addParam(exportContext: ExportContext, request: Request, param: Param) {
        //NOP
    }

    override fun removeParam(exportContext: ExportContext, request: Request, param: Param) {
        //NOP
    }

    override fun addPathParam(exportContext: ExportContext, request: Request, pathParam: PathParam) {
        //NOP
    }

    override fun setJsonBody(exportContext: ExportContext, request: Request, body: Any?, bodyAttr: String?) {
        //NOP
    }

    override fun appendDesc(exportContext: ExportContext, request: Request, desc: String?) {
        //NOP
    }

    override fun addHeader(exportContext: ExportContext, request: Request, header: Header) {
        //NOP
    }

    override fun addResponse(exportContext: ExportContext, request: Request, response: Response) {
        //NOP
    }

    override fun addResponseHeader(exportContext: ExportContext, response: Response, header: Header) {
        //NOP
    }

    override fun setResponseBody(exportContext: ExportContext, response: Response, bodyType: String, body: Any?) {
        //NOP
    }

    override fun setResponseCode(exportContext: ExportContext, response: Response, code: Int) {
        //NOP
    }

    override fun appendResponseBodyDesc(exportContext: ExportContext, response: Response, bodyDesc: String?) {
        //NOP
    }

    override fun startProcessMethod(methodExportContext: MethodExportContext, request: Request) {
        //NOP
    }

    override fun processCompleted(methodExportContext: MethodExportContext, request: Request) {
        val preRequest = ruleComputer.computer(ClassExportRuleKeys.POST_PRE_REQUEST, methodExportContext.element())
        if (preRequest.notNullOrBlank()) {
            request.setExt(ClassExportRuleKeys.POST_PRE_REQUEST.name(), preRequest)
        }

        val test = ruleComputer.computer(ClassExportRuleKeys.POST_TEST, methodExportContext.element())
        if (test.notNullOrBlank()) {
            request.setExt(ClassExportRuleKeys.POST_TEST.name(), test)
        }
    }
}