package com.itangcent.idea.plugin.api.export.feign

import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.itangcent.common.kit.equalIgnoreCase
import com.itangcent.common.model.Header
import com.itangcent.common.model.PathParam
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.common.utils.Extensible
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.condition.annotation.ConditionOnClass
import com.itangcent.idea.plugin.api.export.condition.ConditionOnSimple
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.plugin.api.export.spring.SpringRequestClassExporter
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import org.apache.commons.lang.StringUtils.lowerCase

/**
 * Support export apis from client that annotated with @FeignClient
 *
 * @author tangcent
 */
@Singleton
@ConditionOnSimple(false)
@ConditionOnClass(SpringFeignClassName.REQUEST_LINE_ANNOTATION)
@ConditionOnSetting("feignEnable")
open class FeignRequestClassExporter : SpringRequestClassExporter() {

    override fun hasApi(psiClass: PsiClass): Boolean {
        return annotationHelper.hasAnn(psiClass, SpringFeignClassName.FEIGN_CLIENT_ANNOTATION)
                || (ruleComputer.computer(ClassExportRuleKeys.IS_FEIGN_CTRL, psiClass) ?: false)
    }

    override fun processClass(cls: PsiClass, classExportContext: ClassExportContext) {

        var basePath: URL = URL.nil()

        val pathInFeignClient =
            annotationHelper.findAttrAsString(cls, SpringFeignClassName.FEIGN_CLIENT_ANNOTATION, "path")
        if (pathInFeignClient.notNullOrEmpty()) {
            basePath = basePath.concat(URL.of(pathInFeignClient))
        }

        val ctrlRequestMappingAnn = findRequestMappingInAnn(cls)
        if (ctrlRequestMappingAnn.notNullOrEmpty()) {
            basePath = basePath.concat(findHttpPath(ctrlRequestMappingAnn))
            val ctrlHttpMethod = findHttpMethod(ctrlRequestMappingAnn)
            classExportContext.setExt("ctrlHttpMethod", ctrlHttpMethod)
        }

        val prefixPath = ruleComputer.computer(ClassExportRuleKeys.CLASS_PREFIX_PATH, cls)
        if (prefixPath.notNullOrBlank()) {
            basePath = URL.of(prefixPath).concat(basePath)
        }
        classExportContext.setExt("basePath", basePath)
    }

    override fun processMethod(methodExportContext: MethodExportContext, request: Request) {
        super.processMethod(methodExportContext, request)

        val methodPsiElement = methodExportContext.psi()

        //resolve path variable
        request.path?.urls()?.forEach { url ->
            resolveTemplate(url) { variables ->
                methodExportContext.addVariables(variables)
                for (variable in variables) {
                    val pathParam = PathParam()
                    pathParam.name = variable
                    requestBuilderListener.addPathParam(methodExportContext, request, pathParam)
                    methodExportContext.setExt("$variable-ref", pathParam)
                }
            }
        }

        //resolve @Headers
        val headers = annotationHelper.findAttr(methodPsiElement, SpringFeignClassName.HEADERS_ANNOTATION)
        if (headers != null) {
            if (headers is Array<*>) {
                headers.forEach { header ->
                    resolveHeader(header, methodExportContext, request)
                }
            } else if (headers is String) {
                resolveHeader(headers, methodExportContext, request)
            }
        }

        //resolve @Body
        val body = annotationHelper.findAttrAsString(methodPsiElement, SpringFeignClassName.BODY_ANNOTATION)
        if (body != null) {
            resolveTemplate(methodExportContext, body)
        }
    }

    private fun resolveHeader(
        header: Any?,
        methodExportContext: MethodExportContext,
        request: Request
    ) {
        (header as? String)?.let { resolveHeader(it) }
            ?.let {
                val name = it.first
                val value = it.second.trim()
                if (name.equalIgnoreCase("content-type")) {
                    if (lowerCase(value).contains("application/json")) {
                        methodExportContext.setExt("paramType", "body")
                    }
                }
                val headerInRequest = requestBuilderListener.addHeader(methodExportContext, request, name, value)
                resolveTemplate(value) { variables ->
                    methodExportContext.addVariables(variables)
                    for (variable in variables) {
                        methodExportContext.setExt("$variable-ref", headerInRequest)
                    }
                }
            }
    }

    private fun resolveTemplate(
        methodExportContext: MethodExportContext,
        value: String
    ) {
        resolveTemplate(value) {
            methodExportContext.addVariables(it)
        }
    }

    private fun resolveTemplate(
        value: String,
        handle: (List<String>) -> Unit
    ) {
        FeignTemplate.parseVariables(value)
            .takeIf { it.notNullOrEmpty() }
            ?.let { handle(it) }
    }

    override fun processMethodParameter(
        request: Request,
        parameterExportContext: ParameterExportContext,
        paramDesc: String?
    ) {
        val parameter = parameterExportContext.psi()

        //resolve @Param
        val paramAnn = annotationHelper.findAttrAsString(parameter, SpringFeignClassName.PARAM_ANNOTATION)
        if (paramAnn != null) {

            val readParamDefaultValue = readParamDefaultValue(parameterExportContext.element())

            var ultimateComment = (paramDesc ?: "")
            parameterExportContext.type()?.let { duckType ->
                commentResolver.resolveCommentForType(duckType, parameterExportContext.psi())?.let {
                    ultimateComment = "$ultimateComment $it"
                }
            }

            val ref = parameterExportContext.methodContext()?.getExt<Any>("$paramAnn-ref")
            if (ref != null) {
                when (ref) {
                    is Header -> {
                        ref.desc = ultimateComment
                    }

                    is PathParam -> {
                        ref.desc = ultimateComment
                    }
                }
                return
            }

            addParamAsQuery(
                parameterExportContext, request, readParamDefaultValue
                    ?: parameterExportContext.unbox(), ultimateComment
            )
            return
        }

        super.processMethodParameter(request, parameterExportContext, paramDesc)
    }

    private fun resolveHeader(header: String): Pair<String, String>? {
        val index = header.indexOf(':')
        if (index == -1) {
            logger.info("illegal header:$header")
            return null
        }
        return header.substring(0, index) to header.substring(index + 1)
    }

    private fun Extensible.addVariables(variables: List<String>) {
        var cacheVariables = this.getExt<List<String>>("VARIABLES")
        if (cacheVariables == null) {
            cacheVariables = ArrayList<String>().also { it.addAll(variables) }
            this.setExt("VARIABLES", cacheVariables)
        } else {
            (cacheVariables as MutableList<String>).addAll(variables)
        }
    }
}