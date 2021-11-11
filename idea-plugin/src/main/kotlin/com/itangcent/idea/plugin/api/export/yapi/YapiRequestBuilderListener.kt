package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.itangcent.common.constant.Attrs
import com.itangcent.common.model.*
import com.itangcent.common.utils.Extensible
import com.itangcent.common.utils.getAs
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.api.export.condition.ConditionOnChannel
import com.itangcent.idea.plugin.api.export.core.ExportContext
import com.itangcent.idea.plugin.api.export.core.MethodExportContext
import com.itangcent.idea.plugin.api.export.core.RequestBuilderListener
import com.itangcent.idea.plugin.api.export.core.paramContext
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.config.rule.computer
import org.apache.commons.lang3.StringUtils

/**
 *
 * 1.support rule:[com.itangcent.idea.plugin.api.export.yapi.YapiClassExportRuleKeys.TAG]
 * 2.support rule:[com.itangcent.idea.plugin.api.export.yapi.YapiClassExportRuleKeys.STATUS]
 * 3.support rule:[com.itangcent.idea.plugin.api.export.yapi.YapiClassExportRuleKeys.OPEN]
 */
@ConditionOnChannel("yapi")
class YapiRequestBuilderListener : RequestBuilderListener {

    @Inject
    protected lateinit var ruleComputer: RuleComputer

    @Inject
    private lateinit var configReader: ConfigReader

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
        val key = exportContext.getExt<String>("key")
        if (key == null) {
            trySetDemo(exportContext, formParam)
        } else {
            val parent = exportContext.getExt<Any>("parent") as? Map<*, *> ?: return
            parent.getAs<String>(Attrs.EXAMPLE_ATTR, key)?.let { formParam.setExample(it) }
        }
    }

    override fun addParam(exportContext: ExportContext, request: Request, param: Param) {
        val key = exportContext.getExt<String>("key")
        if (key == null) {
            trySetDemo(exportContext, param)
        } else {
            val parent = exportContext.getExt<Any>("parent") as? Map<*, *> ?: return
            parent.getAs<String>(Attrs.EXAMPLE_ATTR, key)?.let { param.setExample(it) }
        }
    }

    private fun trySetDemo(exportContext: ExportContext, extensible: Extensible) {
        val paramContext = exportContext.paramContext() ?: return
        val demo = ruleComputer.computer(YapiClassExportRuleKeys.PARAM_DEMO, paramContext.parameter)
        if (demo.notNullOrBlank()) {
            extensible.setExample(demo)
        }
    }

    override fun removeParam(exportContext: ExportContext, request: Request, param: Param) {
        //NOP
    }

    override fun addPathParam(exportContext: ExportContext, request: Request, pathParam: PathParam) {
        trySetDemo(exportContext, pathParam)
    }

    override fun setJsonBody(exportContext: ExportContext, request: Request, body: Any?, bodyAttr: String?) {
        //NOP
    }

    override fun appendDesc(exportContext: ExportContext, request: Request, desc: String?) {
        //NOP
    }

    override fun addHeader(exportContext: ExportContext, request: Request, header: Header) {
        trySetDemo(exportContext, header)
    }

    override fun addResponse(exportContext: ExportContext, request: Request, response: Response) {
        //NOP
    }

    override fun addResponseHeader(exportContext: ExportContext, response: Response, header: Header) {
        //NOP
    }

    override fun setResponseBody(exportContext: ExportContext, response: Response, bodyType: String, body: Any?) {
        trySetDemo(exportContext, response)
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

        val tags = ruleComputer.computer(YapiClassExportRuleKeys.TAG, methodExportContext.method)
        if (tags.notNullOrEmpty()) {
            request.setTags(StringUtils.split(tags, configReader.first("api.tag.delimiter")?.let { it + "\n" }
                    ?: ",\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() })
        }

        val status = ruleComputer.computer(YapiClassExportRuleKeys.STATUS, methodExportContext.method)
        request.setStatus(status)

        val open = ruleComputer.computer(YapiClassExportRuleKeys.OPEN, methodExportContext.method)
        request.setOpen(open)
    }
}