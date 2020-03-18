package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.common.model.Request
import com.itangcent.common.utils.KV
import com.itangcent.common.utils.append
import com.itangcent.idea.plugin.api.export.SpringRequestClassExporter
import com.itangcent.idea.plugin.api.export.addParam
import com.itangcent.idea.plugin.api.export.addPathParam
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.jvm.element.ExplicitMethod

open class YapiSpringRequestClassExporter : SpringRequestClassExporter() {

    override fun processCompleted(method: ExplicitMethod, kv: KV<String, Any?>, request: Request) {
        super.processCompleted(method, kv, request)

        val tags = ruleComputer!!.computer(YapiClassExportRuleKeys.TAG, method)
        if (!tags.isNullOrBlank()) {
            request.setTags(tags.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() })
        }

        val status = ruleComputer.computer(YapiClassExportRuleKeys.STATUS, method)
        request.setStatus(status)
    }


    override fun resolveParamStr(request: Request, params: String) {
        when {
            params.startsWith("!") -> {
                requestHelper!!.appendDesc(request, "parameter [${params.removeSuffix("!")}] should not be present")
            }
            params.contains("!=") -> {
                val name = params.substringBefore("!=").trim()
                val value = params.substringAfter("!=").trim()
                val param = request.querys?.find { it.name == name }
                if (param == null) {
                    requestHelper!!.appendDesc(request, "parameter [$name] " +
                            "should not equal to [$value]")
                } else {
                    param.desc = param.desc.append("should not equal to [$value]", "\n")
                }
            }
            !params.contains('=') -> {
                val param = request.querys?.find { it.name == params }
                if (enableUrlTemplating()) {
                    addParamToPath(request, params, "{$params}", param?.desc)
                    param?.let { requestHelper!!.removeParam(request, it) }
                } else {
                    if (param == null) {
                        requestHelper!!.addParam(request, params, null, true, null)
                    } else {
                        param.required = true
                    }
                }
            }
            else -> {
                val name = params.substringBefore("=").trim()
                val value = params.substringAfter("=").trim()
                val param = request.querys?.find { it.name == name }

                if (enableUrlTemplating()) {
                    addParamToPath(request, name, value, param?.desc)
                    param?.let { requestHelper!!.removeParam(request, it) }
                } else {
                    if (param == null) {
                        requestHelper!!.addParam(request, name, value, true, null)
                    } else {
                        param.required = true
                        param.value = value
                    }
                }
            }
        }
    }

    protected open fun addParamToPath(request: Request,
                                      paramName: String,
                                      value: String,
                                      desc: String?) {
        val path = request.path ?: ""
        request.path = when {
            path.endsWith('?') -> "$path$paramName=$value"
            path.contains('?') -> "$path&$paramName=$value"
            else -> "$path?$paramName=$value"
        }
        requestHelper!!.addPathParam(request, paramName, value, desc)
    }

    protected fun enableUrlTemplating(): Boolean {
        return settingBinder!!.read().enableUrlTemplating
    }
}