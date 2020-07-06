package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.model.*
import com.itangcent.common.utils.*
import com.itangcent.idea.plugin.api.export.rule.RequestRuleWrap
import com.itangcent.idea.plugin.utils.SpringClassName
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.jvm.AnnotationHelper
import com.itangcent.intellij.jvm.element.ExplicitMethod
import com.itangcent.intellij.jvm.element.ExplicitParameter
import com.itangcent.intellij.util.hasFile

open class SpringRequestClassExporter : AbstractRequestClassExporter() {

    @Inject
    private val annotationHelper: AnnotationHelper? = null

    @Inject
    private val commentResolver: CommentResolver? = null

    override fun processClass(cls: PsiClass, kv: KV<String, Any?>) {

        val ctrlRequestMappingAnn = findRequestMapping(cls)
        var basePath: URL = findHttpPath(ctrlRequestMappingAnn)
        val prefixPath = ruleComputer!!.computer(ClassExportRuleKeys.CLASS_PREFIX_PATH, cls)
        if (prefixPath.notNullOrBlank()) {
            basePath = URL.of(prefixPath).concat(basePath)
        }

        val ctrlHttpMethod = findHttpMethod(ctrlRequestMappingAnn)

        kv["basePath"] = basePath
        kv["ctrlHttpMethod"] = ctrlHttpMethod
    }

    override fun hasApi(psiClass: PsiClass): Boolean {
        return SpringClassName.SPRING_CONTROLLER_ANNOTATION.any {
            annotationHelper!!.hasAnn(psiClass, it)
        }
    }

    override fun isApi(psiMethod: PsiMethod): Boolean {
        return SpringClassName.SPRING_REQUEST_MAPPING_ANNOTATIONS.any {
            annotationHelper!!.hasAnn(psiMethod, it)
        }
    }

    override fun processMethodParameter(request: Request, param: ExplicitParameter, typeObject: Any?, paramDesc: String?) {

        //RequestBody(json)
        if (isRequestBody(param.psi())) {
            setRequestBody(request, typeObject, paramDesc)
            return
        }

        //ModelAttr(form)
        if (isModelAttr(param.psi())) {
            if (request.method == HttpMethod.NO_METHOD) {
                requestHelper!!.setMethod(request,
                        ruleComputer!!.computer(ClassExportRuleKeys.METHOD_DEFAULT_HTTP_METHOD, param.containMethod())
                                ?: HttpMethod.POST)
            }
            if (request.method == HttpMethod.GET) {
                addParamAsQuery(param, typeObject, request)
            } else {
                addParamAsForm(param, request, typeObject, paramDesc)
            }
            return
        }

        var ultimateComment = (paramDesc ?: "")
        param.getType()?.let { duckType ->
            commentResolver!!.resolveCommentForType(duckType, param.psi())?.let {
                ultimateComment = "$ultimateComment $it"
            }
        }

        //head
        val requestHeaderAnn = findRequestHeader(param.psi())
        if (requestHeaderAnn != null) {

            var headName = requestHeaderAnn.any("value", "name")
            if (headName.anyIsNullOrEmpty()) {
                headName = param.name()
            }

            var required = findRequired(requestHeaderAnn)
            if (!required && ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, param) == true) {
                required = true
            }

            val defaultValue = findDefaultValue(requestHeaderAnn) ?: ""

            val header = Header()
            header.name = headName?.toString()
            header.value = defaultValue
            header.desc = ultimateComment
            header.required = required
            requestHelper!!.addHeader(request, header)
            return
        }

        //path
        val pathVariableAnn = findPathVariable(param.psi())
        if (pathVariableAnn != null) {

            var pathName = pathVariableAnn["value"]?.toString()

            if (pathName == null) {
                pathName = param.name()
            }

            requestHelper!!.addPathParam(request, pathName, ultimateComment)
            return
        }

        //cookie
        val cookieValueAnn = findCookieValue(param.psi())
        if (cookieValueAnn != null) {

            var cookieName = cookieValueAnn["value"]?.toString()

            if (cookieName == null) {
                cookieName = param.name()
            }

            var required = findRequired(cookieValueAnn)
            if (!required && ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, param) == true) {
                required = true
            }

            requestHelper!!.appendDesc(request, if (required) {
                "\nNeed cookie:$cookieName ($ultimateComment)"
            } else {
                val defaultValue = findDefaultValue(cookieValueAnn)
                if (defaultValue.isNullOrBlank()) {
                    "\nCookie:$cookieName ($ultimateComment)"
                } else {
                    "\nCookie:$cookieName=$defaultValue ($ultimateComment)"
                }
            })

            return
        }


        //form/body/query
        var paramType: String? = null

        var paramName: String? = null
        var required = false
        var defaultVal: Any? = null

        val requestParamAnn = findRequestParam(param.psi())

        if (requestParamAnn != null) {
            paramName = findParamName(requestParamAnn)
            required = findRequired(requestParamAnn)

            defaultVal = findDefaultValue(requestParamAnn) ?: ""

            if (request.method == "GET") {
                paramType = "query"
            }
        }

        val readParamDefaultValue = readParamDefaultValue(param)

        if (readParamDefaultValue.notNullOrBlank()) {
            defaultVal = readParamDefaultValue
        }

        if (!required && ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, param) == true) {
            required = true
        }

        if (paramName.isNullOrBlank()) {
            paramName = param.name()
        }

        if (request.method == HttpMethod.GET) {
            addParamAsQuery(param, typeObject, request, ultimateComment)
            return
        }

        if (paramType.isNullOrBlank()) {
            paramType = ruleComputer!!.computer(ClassExportRuleKeys.PARAM_HTTP_TYPE,
                    param)
        }

        if (paramType.notNullOrBlank()) {
            when (paramType) {
                "body" -> {
                    setRequestBody(request, typeObject, ultimateComment)
                    return
                }
                "form" -> {
                    addParamAsForm(param, request, typeObject, ultimateComment)
                    return
                }
                "query" -> {
                    addParamAsQuery(param, typeObject, request, ultimateComment)
                    return
                }
                else -> {
                    logger!!.warn("Unknown param type:$paramType." +
                            "Return of rule `param.without.ann.type`" +
                            "should be `body/form/query`")
                }
            }
        }

        if (typeObject.hasFile()) {
            addParamAsForm(param, request, typeObject, ultimateComment)
            return
        }

        if (defaultVal != null) {
            requestHelper!!.addParam(request,
                    paramName
                    , defaultVal.toString()
                    , required
                    , ultimateComment)
            return
        }

        if (request.canHasForm()) {
            addParamAsForm(param, request, typeObject, ultimateComment)
            return
        }

        //else
        addParamAsQuery(param, typeObject, request, ultimateComment)
    }

    override fun processMethod(method: ExplicitMethod, kv: KV<String, Any?>, request: Request) {
        super.processMethod(method, kv, request)

        val basePath: URL = kv.getAs("basePath")
        val ctrlHttpMethod: String? = kv.getAs("ctrlHttpMethod")
        val requestMapping = findRequestMappingInAnn(method.psi())
        kv["requestMapping"] = requestMapping
        var httpMethod = findHttpMethod(requestMapping)
        if (httpMethod == HttpMethod.NO_METHOD && ctrlHttpMethod != HttpMethod.NO_METHOD) {
            httpMethod = ctrlHttpMethod!!
        }
        request.method = httpMethod

        val httpPath = basePath.concat(findHttpPath(requestMapping))
        requestHelper!!.setPath(request, httpPath)
    }

    override fun processCompleted(method: ExplicitMethod, kv: KV<String, Any?>, request: Request) {
        super.processCompleted(method, kv, request)

        val requestMapping: Pair<Map<String, Any?>, String>? = kv.getAs("requestMapping")
        requestMapping?.let {
            resolveParamInRequestMapping(request, it)
            resolveHeaderInRequestMapping(request, it)
        }

        ruleComputer!!.computer(ClassExportRuleKeys.AFTER_EXPORT, method) {
            it.setExt("api", RequestRuleWrap(request))
        }
    }

    //region process spring annotation-------------------------------------------------------------------

    private fun findHttpPath(requestMappingAnn: Pair<Map<String, Any?>, String>?): URL {
        val path = requestMappingAnn?.first.any("path", "value")
        return when (path) {
            null -> URL.nil()
            is Array<*> -> URL.of(path.mapNotNull { it?.toString() })
            else -> URL.of(path.toString())
        }
    }

    protected open fun resolveParamInRequestMapping(request: Request, requestMappingAnn: Pair<Map<String, Any?>, String>) {
        val params = requestMappingAnn.first["params"] ?: return
        if (params is Array<*>) {
            params.stream()
                    .map { it.tinyString() }
                    .filter { it.notNullOrEmpty() }
                    .forEach { resolveParamStr(request, it!!) }
        } else {
            params.tinyString()
                    ?.takeIf { it.notNullOrEmpty() }
                    ?.let { resolveParamStr(request, it) }
        }
    }

    protected open fun resolveParamStr(request: Request, params: String) {
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
                if (param == null) {
                    requestHelper!!.addParam(request, params, null, true, null)
                } else {
                    param.required = true
                }
            }
            else -> {
                val name = params.substringBefore("=").trim()
                val value = params.substringAfter("=").trim()
                val param = request.querys?.find { it.name == name }
                if (param == null) {
                    requestHelper!!.addParam(request, name, value, true, null)
                } else {
                    param.required = true
                    param.value = value
                }
            }
        }
    }

    protected open fun resolveHeaderInRequestMapping(request: Request, requestMappingAnn: Pair<Map<String, Any?>, String>) {
        val headers = requestMappingAnn.first["headers"] ?: return
        if (headers is Array<*>) {
            headers.stream()
                    .map { it.tinyString() }
                    .filter { it.notNullOrEmpty() }
                    .forEach { resolveHeaderStr(request, it!!) }
        } else {
            headers.tinyString()
                    ?.takeIf { it.notNullOrEmpty() }
                    ?.let { resolveHeaderStr(request, it) }
        }
    }

    protected open fun resolveHeaderStr(request: Request, headers: String) {
        when {
            headers.startsWith("!") -> {
                requestHelper!!.appendDesc(request, "header [${headers.removeSuffix("!")}] should not be present")
            }
            headers.contains("!=") -> {
                val name = headers.substringBefore("!=").trim()
                val value = headers.substringAfter("!=").trim()
                val header = request.querys?.find { it.name == name }
                if (header == null) {
                    requestHelper!!.appendDesc(request, "\nheader [$name] " +
                            "should not equal to [$value]")
                } else {
                    header.desc = header.desc.append("should not equal to [$value]", "\n")
                }
            }
            !headers.contains('=') -> {
                val header = request.querys?.find { it.name == headers }
                if (header == null) {
                    requestHelper!!.addHeader(request, headers, "")
                } else {
                    header.required = true
                }
            }
            else -> {
                val name = headers.substringBefore("=").trim()
                val value = headers.substringAfter("=").trim()
                val header = request.querys?.find { it.name == name }
                if (header == null) {
                    requestHelper!!.addHeader(request, name, value)
                } else {
                    header.required = true
                    header.value = value
                }
            }
        }
    }

    private fun findHttpMethod(requestMappingAnn: Pair<Map<String, Any?>, String>?): String {
        if (requestMappingAnn != null) {
            when {
                requestMappingAnn.second == SpringClassName.REQUEST_MAPPING_ANNOTATION -> {
                    var method = requestMappingAnn.first["method"].tinyString() ?: return HttpMethod.NO_METHOD
                    if (method.contains(",")) {
                        method = method.substringBefore(",")
                    }
                    return when {
                        method.isNullOrBlank() -> {
                            HttpMethod.NO_METHOD
                        }
                        method.startsWith("RequestMethod.") -> {
                            method.removePrefix("RequestMethod.")
                        }
                        method.contains("RequestMethod.") -> {
                            method.substringAfterLast("RequestMethod.")
                        }
                        else -> method
                    }
                }
                requestMappingAnn.second == SpringClassName.GET_MAPPING -> return HttpMethod.GET
                requestMappingAnn.second == SpringClassName.POST_MAPPING -> return HttpMethod.POST
                requestMappingAnn.second == SpringClassName.DELETE_MAPPING -> return HttpMethod.DELETE
                requestMappingAnn.second == SpringClassName.PATCH_MAPPING -> return HttpMethod.PATCH
                requestMappingAnn.second == SpringClassName.PUT_MAPPING -> return HttpMethod.PUT
            }
        }
        return HttpMethod.NO_METHOD
    }

    private fun findRequestMapping(psiClass: PsiClass): Pair<Map<String, Any?>, String>? {
        val requestMappingAnn = findRequestMappingInAnn(psiClass)
        if (requestMappingAnn != null) return requestMappingAnn
        var superCls = psiClass.superClass
        while (superCls != null) {
            val requestMappingAnnInSuper = findRequestMappingInAnn(superCls)
            if (requestMappingAnnInSuper != null) return requestMappingAnnInSuper
            superCls = superCls.superClass
        }
        return null
    }

    private fun findRequestMappingInAnn(ele: PsiElement): Pair<Map<String, Any?>, String>? {
        return SpringClassName.SPRING_REQUEST_MAPPING_ANNOTATIONS
                .stream()
                .map { ann -> annotationHelper!!.findAnnMap(ele, ann)?.to(ann) }
                .firstOrNull { it != null }
    }

    private fun isRequestBody(parameter: PsiParameter): Boolean {
        return annotationHelper!!.hasAnn(parameter, SpringClassName.REQUEST_BODY_ANNOTATION)
    }

    private fun isModelAttr(parameter: PsiParameter): Boolean {
        return annotationHelper!!.hasAnn(parameter, SpringClassName.MODEL_ATTRIBUTE_ANNOTATION)
    }

    private fun findRequestHeader(parameter: PsiParameter): Map<String, Any?>? {
        return annotationHelper!!.findAnnMap(parameter, SpringClassName.REQUEST_HEADER)
    }

    private fun findPathVariable(parameter: PsiParameter): Map<String, Any?>? {
        return annotationHelper!!.findAnnMap(parameter, SpringClassName.PATH_VARIABLE_ANNOTATION)
    }

    private fun findCookieValue(parameter: PsiParameter): Map<String, Any?>? {
        return annotationHelper!!.findAnnMap(parameter, SpringClassName.COOKIE_VALUE_ANNOTATION)
    }

    private fun findRequestParam(parameter: PsiParameter): Map<String, Any?>? {
        return annotationHelper!!.findAnnMap(parameter, SpringClassName.REQUEST_PARAM_ANNOTATION)
    }

    private fun findParamName(requestParamAnn: Map<String, Any?>?): String? {
        return requestParamAnn.any("name", "value")?.toString()
    }

    private fun findRequired(annMap: Map<String, Any?>): Boolean {
        val required = annMap["required"]?.toString()
        return when {
            required?.contains("false") == true -> false
            else -> true
        }
    }

    private fun findDefaultValue(annMap: Map<String, Any?>): String? {
        val defaultValue = annMap["defaultValue"]?.toString()
        if (defaultValue == null
                || defaultValue == SpringClassName.ESCAPE_REQUEST_HEADER_DEFAULT_NONE
                || defaultValue == SpringClassName.REQUEST_HEADER_DEFAULT_NONE) {
            return null
        }
        return defaultValue
    }

    //endregion process spring annotation-------------------------------------------------------------------

}
