package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.model.Header
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.common.utils.*
import com.itangcent.idea.condition.annotation.ConditionOnClass
import com.itangcent.idea.plugin.api.export.condition.ConditionOnSimple
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.jvm.AnnotationHelper
import com.itangcent.intellij.util.hasFile

/**
 * Support export apis from spring controllers.
 *
 * @author tangcent
 */
@Singleton
@ConditionOnSimple(false)
@ConditionOnClass(SpringClassName.REQUEST_MAPPING_ANNOTATION)
open class SpringRequestClassExporter : RequestClassExporter() {

    @Inject
    protected val annotationHelper: AnnotationHelper? = null

    @Inject
    protected val commentResolver: CommentResolver? = null

    override fun processClass(cls: PsiClass, classExportContext: ClassExportContext) {

        val ctrlRequestMappingAnn = findRequestMapping(cls)
        var basePath: URL = findHttpPath(ctrlRequestMappingAnn)
        val prefixPath = ruleComputer.computer(ClassExportRuleKeys.CLASS_PREFIX_PATH, cls)
        if (prefixPath.notNullOrBlank()) {
            basePath = URL.of(prefixPath).concat(basePath)
        }

        val ctrlHttpMethod = findHttpMethod(ctrlRequestMappingAnn)

        classExportContext.setExt("basePath", basePath)
        classExportContext.setExt("ctrlHttpMethod", ctrlHttpMethod)
    }

    override fun hasApi(psiClass: PsiClass): Boolean {
        return SpringClassName.SPRING_CONTROLLER_ANNOTATION.any {
            annotationHelper!!.hasAnn(psiClass, it)
        } || (ruleComputer.computer(ClassExportRuleKeys.IS_CTRL, psiClass) ?: false)
    }

    override fun isApi(psiMethod: PsiMethod): Boolean {
        return SpringClassName.SPRING_REQUEST_MAPPING_ANNOTATIONS.any {
            annotationHelper!!.hasAnn(psiMethod, it)
        }
    }

    override fun processMethodParameter(
        request: Request,
        parameterExportContext: ParameterExportContext,
        paramDesc: String?
    ) {

        //RequestBody(json)
        if (isRequestBody(parameterExportContext.psi())) {
            setRequestBody(
                parameterExportContext,
                request, parameterExportContext.raw(), paramDesc
            )
            return
        }

        //ModelAttr(form)
        if (isModelAttr(parameterExportContext.psi())) {
            if (request.method == HttpMethod.NO_METHOD) {
                requestBuilderListener.setMethod(
                    parameterExportContext, request,
                    ruleComputer.computer(
                        ClassExportRuleKeys.METHOD_DEFAULT_HTTP_METHOD,
                        parameterExportContext.parameter.containMethod()
                    )
                        ?: HttpMethod.POST
                )
            }
            if (request.method == HttpMethod.GET) {
                addParamAsQuery(parameterExportContext, request, parameterExportContext.unbox())
            } else {
                addParamAsForm(parameterExportContext, request, parameterExportContext.unbox(), paramDesc)
            }
            return
        }

        var ultimateComment = (paramDesc ?: "")
        parameterExportContext.parameter.getType()?.let { duckType ->
            commentResolver!!.resolveCommentForType(duckType, parameterExportContext.psi())?.let {
                ultimateComment = "$ultimateComment $it"
            }
        }

        //head
        val requestHeaderAnn = findRequestHeader(parameterExportContext.psi())
        if (requestHeaderAnn != null) {

            var headName = requestHeaderAnn.any("value", "name")
            if (headName.anyIsNullOrEmpty()) {
                headName = parameterExportContext.name()
            }

            var required = findRequired(requestHeaderAnn)
            if (!required && ruleComputer.computer(
                    ClassExportRuleKeys.PARAM_REQUIRED,
                    parameterExportContext.parameter
                ) == true
            ) {
                required = true
            }

            val defaultValue = findDefaultValue(requestHeaderAnn) ?: ""

            val header = Header()
            header.name = headName?.toString()
            header.value = defaultValue
            header.desc = ultimateComment
            header.required = required
            requestBuilderListener.addHeader(parameterExportContext, request, header)
            return
        }

        //path
        val pathVariableAnn = findPathVariable(parameterExportContext.psi())
        if (pathVariableAnn != null) {

            var pathName = pathVariableAnn["value"]?.toString()

            if (pathName == null) {
                pathName = parameterExportContext.name()
            }

            requestBuilderListener.addPathParam(parameterExportContext, request, pathName, ultimateComment)
            return
        }

        //cookie
        val cookieValueAnn = findCookieValue(parameterExportContext.psi())
        if (cookieValueAnn != null) {

            var cookieName = cookieValueAnn["value"]?.toString()

            if (cookieName == null) {
                cookieName = parameterExportContext.name()
            }

            var required = findRequired(cookieValueAnn)
            if (!required && ruleComputer.computer(
                    ClassExportRuleKeys.PARAM_REQUIRED,
                    parameterExportContext.parameter
                ) == true
            ) {
                required = true
            }

            requestBuilderListener.appendDesc(
                parameterExportContext,
                request, if (required) {
                    "\nNeed cookie:$cookieName ($ultimateComment)"
                } else {
                    val defaultValue = findDefaultValue(cookieValueAnn)
                    if (defaultValue.isNullOrBlank()) {
                        "\nCookie:$cookieName ($ultimateComment)"
                    } else {
                        "\nCookie:$cookieName=$defaultValue ($ultimateComment)"
                    }
                }
            )

            return
        }


        //form/body/query
        var paramType: String? = null

        val requestParamAnn = findRequestParam(parameterExportContext.psi())

        if (requestParamAnn != null) {
            findParamName(requestParamAnn)?.let { parameterExportContext.setName(it) }
            parameterExportContext.setRequired(findRequired(requestParamAnn))
            findDefaultValue(requestParamAnn)?.let { parameterExportContext.setDefaultVal(it) }

            if (request.method == "GET") {
                paramType = "query"
            }
        }

        val readParamDefaultValue = readParamDefaultValue(parameterExportContext.parameter)

        if (readParamDefaultValue.notNullOrBlank()) {
            parameterExportContext.setDefaultVal(readParamDefaultValue!!)
        }

        if (parameterExportContext.required() == null) {
            ruleComputer.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameterExportContext.parameter)?.let {
                parameterExportContext.setRequired(it)
            }
        }

        if (request.method == HttpMethod.GET) {
            addParamAsQuery(parameterExportContext, request, parameterExportContext.unbox(), ultimateComment)
            return
        }

        if (paramType.isNullOrBlank()) {
            paramType = ruleComputer.computer(
                ClassExportRuleKeys.PARAM_HTTP_TYPE,
                parameterExportContext.parameter
            ) ?: "query"
        }

        if (paramType.notNullOrBlank()) {
            when (paramType) {
                "body" -> {
                    requestBuilderListener.setMethodIfMissed(parameterExportContext, request, HttpMethod.POST)
                    setRequestBody(parameterExportContext, request, parameterExportContext.raw(), ultimateComment)
                    return
                }
                "form" -> {
                    requestBuilderListener.setMethodIfMissed(parameterExportContext, request, HttpMethod.POST)
                    addParamAsForm(
                        parameterExportContext, request, parameterExportContext.defaultVal()
                            ?: parameterExportContext.unbox(), ultimateComment
                    )
                    return
                }
                "query" -> {
                    addParamAsQuery(
                        parameterExportContext, request, parameterExportContext.defaultVal()
                            ?: parameterExportContext.unbox(), ultimateComment
                    )
                    return
                }
                else -> {
                    logger.warn(
                        "Unknown param type:$paramType." +
                                "Return of rule `param.without.ann.type`" +
                                "should be `body/form/query`"
                    )
                }
            }
        }

        if (parameterExportContext.unbox().hasFile()) {
            addParamAsForm(parameterExportContext, request, parameterExportContext.unbox(), ultimateComment)
            return
        }

        if (parameterExportContext.defaultVal() != null) {
            requestBuilderListener.addParam(
                parameterExportContext,
                request,
                parameterExportContext.name(),
                parameterExportContext.defaultVal().toString(),
                parameterExportContext.required()
                    ?: false,
                ultimateComment
            )
            return
        }

//        if (request.hasForm()) {
//            addParamAsForm(parameter, request, typeObject, ultimateComment)
//            return
//        }

        //else
        addParamAsQuery(parameterExportContext, request, parameterExportContext.unbox(), ultimateComment)
    }

    override fun processMethod(methodExportContext: MethodExportContext, request: Request) {
        super.processMethod(methodExportContext, request)

        val classExportContext = methodExportContext.classContext()
        val basePath: URL = classExportContext?.getExt("basePath") ?: URL.nil()
        val ctrlHttpMethod: String? = classExportContext?.getExt("ctrlHttpMethod")
        val requestMapping = findRequestMappingInAnn(methodExportContext.psi())
        methodExportContext.setExt("requestMapping", requestMapping)
        var httpMethod = findHttpMethod(requestMapping)
        if (httpMethod == HttpMethod.NO_METHOD
            && ctrlHttpMethod != null
            && ctrlHttpMethod != HttpMethod.NO_METHOD
        ) {
            httpMethod = ctrlHttpMethod
        }
        request.method = httpMethod

        val httpPath = basePath.concat(findHttpPath(requestMapping))
        requestBuilderListener.setPath(methodExportContext, request, httpPath)
    }

    override fun processCompleted(methodExportContext: MethodExportContext, request: Request) {
        val requestMapping: Pair<Map<String, Any?>, String>? = methodExportContext.getExt("requestMapping")
        requestMapping?.let {
            resolveParamInRequestMapping(methodExportContext, request, it)
            resolveHeaderInRequestMapping(methodExportContext, request, it)
        }

        super.processCompleted(methodExportContext, request)
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

    protected open fun resolveParamInRequestMapping(
        methodExportContext: MethodExportContext,
        request: Request, requestMappingAnn: Pair<Map<String, Any?>, String>
    ) {
        val params = requestMappingAnn.first["params"] ?: return
        if (params is Array<*>) {
            params.stream()
                .map { it.tinyString() }
                .filter { it.notNullOrEmpty() }
                .forEach { resolveParamStr(methodExportContext, request, it!!) }
        } else {
            params.tinyString()
                ?.takeIf { it.notNullOrEmpty() }
                ?.let { resolveParamStr(methodExportContext, request, it) }
        }
    }

    protected open fun resolveParamStr(
        methodExportContext: MethodExportContext,
        request: Request, params: String
    ) {
        when {
            params.startsWith("!") -> {
                requestBuilderListener.appendDesc(
                    methodExportContext,
                    request, "parameter [${params.removeSuffix("!")}] should not be present"
                )
            }
            params.contains("!=") -> {
                val name = params.substringBefore("!=").trim()
                val value = params.substringAfter("!=").trim()
                val param = request.querys?.find { it.name == name }
                if (param == null) {
                    requestBuilderListener.appendDesc(
                        methodExportContext,
                        request, "parameter [$name] " +
                                "should not equal to [$value]"
                    )
                } else {
                    param.desc = param.desc.append("should not equal to [$value]", "\n")
                }
            }
            !params.contains('=') -> {
                val param = request.querys?.find { it.name == params }
                if (param == null) {
                    requestBuilderListener.addParam(
                        methodExportContext,
                        request, params, null, true, null
                    )
                } else {
                    param.required = true
                }
            }
            else -> {
                val name = params.substringBefore("=").trim()
                val value = params.substringAfter("=").trim()
                val param = request.querys?.find { it.name == name }
                if (param == null) {
                    requestBuilderListener.addParam(
                        methodExportContext,
                        request, name, value, true, null
                    )
                } else {
                    param.required = true
                    param.value = value
                }
            }
        }
    }

    protected open fun resolveHeaderInRequestMapping(
        methodExportContext: MethodExportContext,
        request: Request, requestMappingAnn: Pair<Map<String, Any?>, String>
    ) {
        val headers = requestMappingAnn.first["headers"] ?: return
        if (headers is Array<*>) {
            headers.stream()
                .map { it.tinyString() }
                .filter { it.notNullOrEmpty() }
                .forEach { resolveHeaderStr(methodExportContext, request, it!!) }
        } else {
            headers.tinyString()
                ?.takeIf { it.notNullOrEmpty() }
                ?.let { resolveHeaderStr(methodExportContext, request, it) }
        }
    }

    protected open fun resolveHeaderStr(methodExportContext: MethodExportContext, request: Request, headers: String) {
        when {
            headers.startsWith("!") -> {
                requestBuilderListener.appendDesc(
                    methodExportContext,
                    request,
                    "header [${headers.removeSuffix("!")}] should not be present"
                )
            }
            headers.contains("!=") -> {
                val name = headers.substringBefore("!=").trim()
                val value = headers.substringAfter("!=").trim()
                val header = request.querys?.find { it.name == name }
                if (header == null) {
                    requestBuilderListener.appendDesc(
                        methodExportContext,
                        request, "\nheader [$name] " +
                                "should not equal to [$value]"
                    )
                } else {
                    header.desc = header.desc.append("should not equal to [$value]", "\n")
                }
            }
            !headers.contains('=') -> {
                val header = request.querys?.find { it.name == headers }
                if (header == null) {
                    requestBuilderListener.addHeader(methodExportContext, request, headers, "")
                } else {
                    header.required = true
                }
            }
            else -> {
                val name = headers.substringBefore("=").trim()
                val value = headers.substringAfter("=").trim()
                val header = request.querys?.find { it.name == name }
                if (header == null) {
                    requestBuilderListener.addHeader(methodExportContext, request, name, value)
                } else {
                    header.required = true
                    header.value = value
                }
            }
        }
    }

    private fun findHttpMethod(requestMappingAnn: Pair<Map<String, Any?>, String>?): String {
        if (requestMappingAnn != null) {
            when (requestMappingAnn.second) {
                SpringClassName.REQUEST_MAPPING_ANNOTATION -> {
                    var method = requestMappingAnn.first["method"].tinyString() ?: return HttpMethod.NO_METHOD
                    if (method.contains(",")) {
                        method = method.substringBefore(",")
                    }
                    return when {
                        method.isBlank() -> {
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
                SpringClassName.GET_MAPPING -> return HttpMethod.GET
                SpringClassName.POST_MAPPING -> return HttpMethod.POST
                SpringClassName.DELETE_MAPPING -> return HttpMethod.DELETE
                SpringClassName.PATCH_MAPPING -> return HttpMethod.PATCH
                SpringClassName.PUT_MAPPING -> return HttpMethod.PUT
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

    protected fun isRequestBody(parameter: PsiParameter): Boolean {
        return annotationHelper!!.hasAnn(parameter, SpringClassName.REQUEST_BODY_ANNOTATION)
    }

    protected fun isModelAttr(parameter: PsiParameter): Boolean {
        return annotationHelper!!.hasAnn(parameter, SpringClassName.MODEL_ATTRIBUTE_ANNOTATION)
    }

    protected fun findRequestHeader(parameter: PsiParameter): Map<String, Any?>? {
        return annotationHelper!!.findAnnMap(parameter, SpringClassName.REQUEST_HEADER)
    }

    protected fun findPathVariable(parameter: PsiParameter): Map<String, Any?>? {
        return annotationHelper!!.findAnnMap(parameter, SpringClassName.PATH_VARIABLE_ANNOTATION)
    }

    protected fun findCookieValue(parameter: PsiParameter): Map<String, Any?>? {
        return annotationHelper!!.findAnnMap(parameter, SpringClassName.COOKIE_VALUE_ANNOTATION)
    }

    protected fun findRequestParam(parameter: PsiParameter): Map<String, Any?>? {
        return annotationHelper!!.findAnnMap(parameter, SpringClassName.REQUEST_PARAM_ANNOTATION)
    }

    protected fun findParamName(requestParamAnn: Map<String, Any?>?): String? {
        return requestParamAnn.any("name", "value")?.toString()
    }

    protected fun findRequired(annMap: Map<String, Any?>): Boolean {
        val required = annMap["required"]?.toString()
        return when {
            required?.contains("false") == true -> false
            else -> true
        }
    }

    protected fun findDefaultValue(annMap: Map<String, Any?>): String? {
        val defaultValue = annMap["defaultValue"]?.toString()
        if (defaultValue == null
            || defaultValue == SpringClassName.ESCAPE_REQUEST_HEADER_DEFAULT_NONE
            || defaultValue == SpringClassName.REQUEST_HEADER_DEFAULT_NONE
        ) {
            return null
        }
        return defaultValue
    }

    //endregion process spring annotation-------------------------------------------------------------------

}
