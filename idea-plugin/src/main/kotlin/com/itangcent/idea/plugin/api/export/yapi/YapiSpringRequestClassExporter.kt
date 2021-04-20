package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.constant.Attrs
import com.itangcent.common.model.Header
import com.itangcent.common.model.Param
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.common.utils.append
import com.itangcent.idea.plugin.api.export.core.MethodExportContext
import com.itangcent.idea.plugin.api.export.core.addParam
import com.itangcent.idea.plugin.api.export.core.addPathParam
import com.itangcent.idea.plugin.api.export.spring.SpringRequestClassExporter
import com.itangcent.idea.plugin.settings.helper.YapiSettingsHelper
import com.itangcent.utils.ExtensibleKit.fromJson

/**
 *
 * 1.support enableUrlTemplating
 */
@Singleton
open class YapiSpringRequestClassExporter : SpringRequestClassExporter() {

    @Inject
    private lateinit var yapiSettingsHelper: YapiSettingsHelper

    override fun resolveParamStr(methodExportContext: MethodExportContext,
                                 request: Request, params: String) {
        when {
            params.startsWith("!") -> {
                requestBuilderListener.appendDesc(methodExportContext,
                        request, "parameter [${params.removeSuffix("!")}] should not be present")
            }
            params.contains("!=") -> {
                val name = params.substringBefore("!=").trim()
                val value = params.substringAfter("!=").trim()
                val param = request.querys?.find { it.name == name }
                if (param == null) {
                    requestBuilderListener.appendDesc(methodExportContext,
                            request, "parameter [$name] " +
                            "should not equal to [$value]")
                } else {
                    param.desc = param.desc.append("should not equal to [$value]", "\n")
                }
            }
            !params.contains('=') -> {
                val param = request.querys?.find { it.name == params }
                if (yapiSettingsHelper.enableUrlTemplating()) {
                    addParamToPath(request, params, "{$params}")
                    requestBuilderListener.addPathParam(methodExportContext,
                            request, params, "", param?.desc)
                    param?.let {
                        requestBuilderListener.removeParam(methodExportContext,
                                request, it)
                    }
                } else {
                    if (param == null) {
                        requestBuilderListener.addParam(methodExportContext,
                                request, params, null, true, null)
                    } else {
                        param.required = true
                    }
                }
            }
            else -> {
                val name = params.substringBefore("=").trim()
                val value = params.substringAfter("=").trim()
                val param = request.querys?.find { it.name == name }

                if (yapiSettingsHelper.enableUrlTemplating()) {
                    addParamToPath(request, name, value)
                    requestBuilderListener.addPathParam(methodExportContext,
                            request, name, value, param?.desc)
                    param?.let { requestBuilderListener.removeParam(methodExportContext, request, it) }
                } else {
                    if (param == null) {
                        requestBuilderListener.addParam(methodExportContext,
                                request, name, value, true, null)
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
                                      value: String) {
        request.path = (request.path ?: URL.nil()).map { path ->
            when {
                path.endsWith('?') -> {
                    return@map "$path$paramName=$value"
                }
                path.contains('?') -> {
                    return@map "$path&$paramName=$value"
                }
                else -> {
                    return@map "$path?$paramName=$value"
                }
            }
        }
    }

    override fun parseHeaderFromJson(headerStr: String) = Header::class.fromJson(headerStr, Attrs.EXAMPLE_ATTR)

    override fun parseParamFromJson(paramStr: String) = Param::class.fromJson(paramStr, Attrs.EXAMPLE_ATTR)
}