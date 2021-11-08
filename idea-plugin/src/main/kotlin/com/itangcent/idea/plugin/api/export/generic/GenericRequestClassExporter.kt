package com.itangcent.idea.plugin.api.export.generic

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.kit.KitUtils
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.plugin.api.export.Orders
import com.itangcent.idea.plugin.api.export.condition.ConditionOnSimple
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.plugin.api.export.generic.GenericClassExportRuleKeys.HTTP_METHOD
import com.itangcent.idea.plugin.api.export.generic.GenericClassExportRuleKeys.HTTP_PATH
import com.itangcent.idea.plugin.api.export.generic.GenericClassExportRuleKeys.PARAM_AS_COOKIE
import com.itangcent.idea.plugin.api.export.generic.GenericClassExportRuleKeys.PARAM_AS_FORM_BODY
import com.itangcent.idea.plugin.api.export.generic.GenericClassExportRuleKeys.PARAM_AS_JSON_BODY
import com.itangcent.idea.plugin.api.export.generic.GenericClassExportRuleKeys.PARAM_AS_PATH_VAR
import com.itangcent.idea.plugin.api.export.generic.GenericClassExportRuleKeys.PARAM_COOKIE
import com.itangcent.idea.plugin.api.export.generic.GenericClassExportRuleKeys.PARAM_COOKIE_VALUE
import com.itangcent.idea.plugin.api.export.generic.GenericClassExportRuleKeys.PARAM_HEADER
import com.itangcent.idea.plugin.api.export.generic.GenericClassExportRuleKeys.PARAM_NAME
import com.itangcent.idea.plugin.api.export.generic.GenericClassExportRuleKeys.PARAM_PATH_VAR
import com.itangcent.idea.plugin.settings.helper.SupportSettingsHelper
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.util.hasFile
import com.itangcent.order.Order
import kotlin.reflect.KClass

/**
 * A generic [RequestClassExporter] that exports [Request]
 * from any ordinary method
 * Depends on [GenericClassExportRuleKeys]
 */
@Order(Orders.GENERIC)
@ConditionOnSimple(false)
open class GenericRequestClassExporter : RequestClassExporter() {

    @Inject
    protected lateinit var supportSettingsHelper: SupportSettingsHelper

    @Inject
    protected val commentResolver: CommentResolver? = null

    override fun support(docType: KClass<*>): Boolean {
        return supportSettingsHelper.genericEnable() && super.support(docType)
    }

    override fun export(cls: Any, docHandle: DocHandle, completedHandle: CompletedHandle): Boolean {
        if (!supportSettingsHelper.genericEnable()) {
            completedHandle(cls)
            return false
        }
        return super.export(cls, docHandle, completedHandle)
    }

    override fun processClass(cls: PsiClass, classExportContext: ClassExportContext) {
        val pathAndMethodOfClass = findPathAndMethod(cls)
        var basePath: URL = URL.of(pathAndMethodOfClass.first)
        val prefixPath = ruleComputer.computer(ClassExportRuleKeys.CLASS_PREFIX_PATH, cls)
        if (prefixPath.notNullOrBlank()) {
            basePath = URL.of(prefixPath).concat(basePath)
        }

        val ctrlHttpMethod = pathAndMethodOfClass.second

        classExportContext.setExt("basePath", basePath)
        classExportContext.setExt("ctrlHttpMethod", ctrlHttpMethod)
    }

    override fun hasApi(psiClass: PsiClass): Boolean {
        return (ruleComputer.computer(GenericClassExportRuleKeys.CLASS_HAS_API, psiClass) ?: false)
    }

    override fun isApi(psiMethod: PsiMethod): Boolean {
        return (ruleComputer.computer(GenericClassExportRuleKeys.METHOD_HAS_API, psiMethod) ?: false)
    }

    override fun processMethodParameter(
        request: Request,
        parameterExportContext: ParameterExportContext,
        paramDesc: String?
    ) {

        //RequestBody(json)
        if (isJsonBody(parameterExportContext.psi())) {
            if (request.method == "GET") {
                logger.warn("Request Body is not supported for GET method, please check the rule [generic.param.as.json.body]")
                addParamAsQuery(
                    parameterExportContext, request, parameterExportContext.defaultVal()
                        ?: parameterExportContext.unbox(), getUltimateComment(paramDesc, parameterExportContext)
                )
                return
            }
            setRequestBody(
                parameterExportContext,
                request, parameterExportContext.raw(), paramDesc
            )
            return
        }

        //ModelAttr(form)
        if (isFormBody(parameterExportContext.psi())) {
            requestBuilderListener.setMethodIfMissed(parameterExportContext, request, HttpMethod.POST)
            if (request.method == HttpMethod.GET) {
                logger.warn("Form is not supported for GET method, it will be resolved as query.")
                addParamAsQuery(parameterExportContext, request, parameterExportContext.unbox())
            } else {
                addParamAsForm(parameterExportContext, request, parameterExportContext.unbox(), paramDesc)
            }
            return
        }

        //head
        val headerStr = findRequestHeader(parameterExportContext.psi())
        if (headerStr != null) {
            cacheAble!!.cache("header" to headerStr) {
                val header = KitUtils.safe { additionalParseHelper.parseHeaderFromJson(headerStr) }
                when {
                    header == null -> {
                        logger.error("error to parse additional header: $headerStr")
                        return@cache null
                    }
                    header.name.isNullOrBlank() -> {
                        logger.error("no name had be found in: $headerStr")
                        return@cache null
                    }
                    else -> return@cache header
                }
            }?.let {
                requestBuilderListener.addHeader(parameterExportContext, request, it)
            }
            return
        }

        val ultimateComment = getUltimateComment(paramDesc, parameterExportContext)

        //path
        if (isPathVariable(parameterExportContext.psi())) {
            var pathName = findPathVariable(parameterExportContext.psi())
            if (pathName == null) {
                pathName = parameterExportContext.name()
            }
            requestBuilderListener.addPathParam(parameterExportContext, request, pathName, ultimateComment)
            return
        }

        if (parameterExportContext.required() == null) {
            ruleComputer.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameterExportContext.parameter)?.let {
                parameterExportContext.setRequired(it)
            }
        }

        //cookie
        if (isCookie(parameterExportContext.psi())) {

            var cookieName = findCookie(parameterExportContext.psi())

            if (cookieName == null) {
                cookieName = parameterExportContext.name()
            }

            requestBuilderListener.appendDesc(
                parameterExportContext,
                request, if (parameterExportContext.required() == true) {
                    "\nNeed cookie:$cookieName ($ultimateComment)"
                } else {
                    val defaultValue = findCookieValue(parameterExportContext.psi())
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

        findParamName(parameterExportContext.psi())?.let { parameterExportContext.setName(it) }

        if (request.method == "GET") {
            paramType = "query"
        }

        val readParamDefaultValue = readParamDefaultValue(parameterExportContext.parameter)

        if (readParamDefaultValue.notNullOrBlank()) {
            parameterExportContext.setDefaultVal(readParamDefaultValue!!)
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

    private fun getUltimateComment(
        paramDesc: String?,
        parameterExportContext: ParameterExportContext
    ): String {
        var ultimateComment = (paramDesc ?: "")
        parameterExportContext.parameter.getType()?.let { duckType ->
            commentResolver!!.resolveCommentForType(duckType, parameterExportContext.psi())?.let {
                ultimateComment = "$ultimateComment $it"
            }
        }
        return ultimateComment
    }

    override fun processMethod(methodExportContext: MethodExportContext, request: Request) {
        super.processMethod(methodExportContext, request)

        val classExportContext = methodExportContext.classContext()
        val basePath: URL = classExportContext?.getExt("basePath") ?: URL.nil()
        val ctrlHttpMethod: String? = classExportContext?.getExt("ctrlHttpMethod")
        val pathAndMethod = findPathAndMethod(methodExportContext.psi())
        methodExportContext.setExt("requestMapping", pathAndMethod)
        var httpMethod = pathAndMethod.second
        if (httpMethod == HttpMethod.NO_METHOD && ctrlHttpMethod != HttpMethod.NO_METHOD) {
            httpMethod = ctrlHttpMethod!!
        }
        request.method = httpMethod

        val httpPath = basePath.concat(URL.of(pathAndMethod.first))
        requestBuilderListener.setPath(methodExportContext, request, httpPath)
    }

    //region parse http info by rules

    /**
     * @return url to method
     */
    private fun findPathAndMethod(psiClass: PsiClass): Pair<String?, String?> {
        var path: String? = null
        var method: String? = null
        var superCls: PsiClass? = psiClass
        while (superCls != null) {
            val p = findPath(superCls)
            if (p != null) {
                path = p
                break
            }
            superCls = superCls.superClass
        }
        superCls = psiClass
        while (superCls != null) {
            val h = findHttpMethod(superCls)
            if (h != null) {
                method = h
                break
            }
            superCls = superCls.superClass
        }
        return path to method
    }

    /**
     * @return url to method
     */
    private fun findPathAndMethod(ele: PsiElement): Pair<String?, String?> {
        return findPath(ele) to findHttpMethod(ele)
    }

    private fun findPath(ele: PsiElement): String? {
        return ruleComputer.computer(HTTP_PATH, ele)
    }

    private fun findHttpMethod(ele: PsiElement): String? {
        return ruleComputer.computer(HTTP_METHOD, ele)
    }

    protected fun isJsonBody(parameter: PsiParameter): Boolean {
        return ruleComputer.computer(PARAM_AS_JSON_BODY, parameter) ?: false
    }

    protected fun isFormBody(parameter: PsiParameter): Boolean {
        return ruleComputer.computer(PARAM_AS_FORM_BODY, parameter) ?: false
    }

    protected fun findRequestHeader(parameter: PsiParameter): String? {
        return ruleComputer.computer(PARAM_HEADER, parameter)
    }

    protected fun isPathVariable(parameter: PsiParameter): Boolean {
        return ruleComputer.computer(PARAM_AS_PATH_VAR, parameter) ?: false
    }

    protected fun findPathVariable(parameter: PsiParameter): String? {
        return ruleComputer.computer(PARAM_PATH_VAR, parameter)
    }

    protected fun isCookie(parameter: PsiParameter): Boolean {
        return ruleComputer.computer(PARAM_AS_COOKIE, parameter) ?: false
    }

    protected fun findCookie(parameter: PsiParameter): String? {
        return ruleComputer.computer(PARAM_COOKIE, parameter)
    }

    protected fun findCookieValue(parameter: PsiParameter): String? {
        return ruleComputer.computer(PARAM_COOKIE_VALUE, parameter)
    }

    protected fun findParamName(parameter: PsiParameter): String? {
        return ruleComputer.computer(PARAM_NAME, parameter)
    }

    //endregion

}
