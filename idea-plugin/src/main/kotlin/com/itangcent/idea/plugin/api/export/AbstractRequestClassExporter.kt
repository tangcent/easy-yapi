package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.itangcent.common.constant.Attrs
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.exception.ProcessCanceledException
import com.itangcent.common.kit.*
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.*
import com.itangcent.common.utils.*
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.api.MethodInferHelper
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.utils.SpringClassName
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.*
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.jvm.element.ExplicitElement
import com.itangcent.intellij.jvm.element.ExplicitMethod
import com.itangcent.intellij.jvm.element.ExplicitParameter
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.intellij.psi.JsonOption
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.*
import java.util.*
import kotlin.reflect.KClass

abstract class AbstractRequestClassExporter : ClassExporter, Worker {

    @Inject
    protected val cacheAble: CacheAble? = null

    override fun support(docType: KClass<*>): Boolean {
        return docType == Request::class
    }

    private var statusRecorder: StatusRecorder = StatusRecorder()

    override fun status(): WorkerStatus {
        return statusRecorder.status()
    }

    override fun waitCompleted() {
        return statusRecorder.waitCompleted()
    }

    override fun cancel() {
        return statusRecorder.cancel()
    }

    @Inject
    protected val logger: Logger? = null

    @Inject
    private val docHelper: DocHelper? = null

    @Inject
    protected val psiClassHelper: PsiClassHelper? = null

    @Inject
    protected val requestHelper: RequestHelper? = null

    @Inject
    protected val settingBinder: SettingBinder? = null

    @Inject
    protected val duckTypeHelper: DuckTypeHelper? = null

    @Inject
    protected val methodReturnInferHelper: MethodInferHelper? = null

    @Inject
    protected val ruleComputer: RuleComputer? = null

    @Inject
    protected val jvmClassHelper: JvmClassHelper? = null

    @Inject(optional = true)
    protected val methodFilter: MethodFilter? = null

    @Inject
    protected var actionContext: ActionContext? = null

    @Inject
    protected var apiHelper: ApiHelper? = null

    @Inject
    private val linkExtractor: LinkExtractor? = null

    @Inject
    private val linkResolver: LinkResolver? = null

    @Inject
    private val contextSwitchListener: ContextSwitchListener? = null

    override fun export(cls: Any, docHandle: DocHandle): Boolean {
        if (cls !is PsiClass) return false
        contextSwitchListener?.switchTo(cls)
        actionContext!!.checkStatus()
        statusRecorder.newWork()
        try {
            when {
                !hasApi(cls) -> return false
                shouldIgnore(cls) -> {
                    logger!!.info("ignore class:" + cls.qualifiedName)
                    return true
                }
                else -> {
                    logger!!.info("search api from:${cls.qualifiedName}")

                    val kv = KV.create<String, Any?>()

                    processClass(cls, kv)

                    foreachMethod(cls) { explicitMethod ->
                        val method = explicitMethod.psi()
                        if (isApi(method) && methodFilter?.checkMethod(method) != false) {
                            try {
                                exportMethodApi(cls, explicitMethod, kv, docHandle)
                            } catch (e: Exception) {
                                logger.traceError("error to export api from method:" + method.name, e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger!!.traceError("error to export api from class:" + cls.name, e)
        } finally {
            statusRecorder.endWork()
        }
        return true
    }

    protected abstract fun processClass(cls: PsiClass, kv: KV<String, Any?>)

    protected abstract fun hasApi(psiClass: PsiClass): Boolean

    protected abstract fun isApi(psiMethod: PsiMethod): Boolean

    open protected fun shouldIgnore(psiElement: PsiElement): Boolean {
        return ruleComputer!!.computer(ClassExportRuleKeys.IGNORE, psiElement) ?: false
    }

    open protected fun shouldIgnore(explicitElement: ExplicitElement<*>): Boolean {
        return ruleComputer!!.computer(ClassExportRuleKeys.IGNORE, explicitElement) ?: false
    }

    private fun exportMethodApi(
            psiClass: PsiClass, method: ExplicitMethod, kv: KV<String, Any?>,
            docHandle: DocHandle
    ) {

        actionContext!!.checkStatus()

        val request = Request()

        request.resource = PsiMethodResource(method.psi(), psiClass)

        processMethod(method, kv, request)

        processMethodParameters(method, request)

        processResponse(method, request)

        processCompleted(method, kv, request)

        docHandle(request)
    }

    protected open fun processMethod(method: ExplicitMethod, kv: KV<String, Any?>, request: Request) {
        apiHelper!!.nameAndAttrOfApi(method, {
            requestHelper!!.setName(request, it)
        }, {
            requestHelper!!.appendDesc(request, it)
        })
    }

    protected open fun readParamDoc(explicitParameter: ExplicitParameter): String? {
        return ruleComputer!!.computer(ClassExportRuleKeys.PARAM_DOC, explicitParameter)
    }

    protected open fun readParamDefaultValue(param: ExplicitParameter): String? {
        return ruleComputer!!.computer(ClassExportRuleKeys.PARAM_DEFAULT_VALUE, param)
    }

    protected open fun processCompleted(method: ExplicitMethod, kv: KV<String, Any?>, request: Request) {
        //parse additionalHeader by config
        val additionalHeader = ruleComputer!!.computer(ClassExportRuleKeys.METHOD_ADDITIONAL_HEADER,
                method)
        if (additionalHeader.notNullOrEmpty()) {
            val additionalHeaders = additionalHeader!!.lines()
            for (headerStr in additionalHeaders) {
                cacheAble!!.cache("header" to headerStr) {
                    val header = KitUtils.safe { GsonUtils.fromJson(headerStr, Header::class) }
                    when {
                        header == null -> {
                            logger!!.error("error to parse additional header: $headerStr")
                            return@cache null
                        }
                        header.name.isNullOrBlank() -> {
                            logger!!.error("no name had be found in: $headerStr")
                            return@cache null
                        }
                        else -> return@cache header
                    }
                }?.let {
                    requestHelper!!.addHeader(request, it)
                }
            }
        }

        //parse additionalParam by config
        val additionalParam = ruleComputer.computer(ClassExportRuleKeys.METHOD_ADDITIONAL_PARAM, method)
        if (additionalParam.notNullOrEmpty()) {
            val additionalParams = additionalParam!!.lines()
            for (paramStr in additionalParams) {
                cacheAble!!.cache("param" to paramStr) {
                    val param = KitUtils.safe { GsonUtils.fromJson(paramStr, Param::class) }
                    when {
                        param == null -> {
                            logger!!.error("error to parse additional param: $paramStr")
                            return@cache null
                        }
                        param.name.isNullOrBlank() -> {
                            logger!!.error("no name had be found in: $paramStr")
                            return@cache null
                        }
                        else -> return@cache param
                    }
                }?.let {
                    requestHelper!!.addParam(request, it)
                }
            }
        }

        //parse additionalResponseHeader by config
        if (request.response.notNullOrEmpty()) {
            val additionalResponseHeader =
                    ruleComputer.computer(ClassExportRuleKeys.METHOD_ADDITIONAL_RESPONSE_HEADER, method)
            if (additionalResponseHeader.notNullOrEmpty()) {
                val additionalHeaders = additionalResponseHeader!!.lines()
                for (headerStr in additionalHeaders) {
                    cacheAble!!.cache("header" to headerStr) {
                        val header = KitUtils.safe { GsonUtils.fromJson(headerStr, Header::class) }
                        when {
                            header == null -> {
                                logger!!.error("error to parse additional response header: $headerStr")
                                return@cache null
                            }
                            header.name.isNullOrBlank() -> {
                                logger!!.error("no name had be found in: $headerStr")
                                return@cache null
                            }
                            else -> return@cache header
                        }
                    }?.let {
                        request.response!!.forEach { response ->
                            requestHelper!!.addResponseHeader(response, it)
                        }
                    }
                }
            }
        }
    }

    protected open fun processResponse(method: ExplicitMethod, request: Request) {

        var returnType: DuckType? = null
        var fromRule = false
        val returnTypeByRule = ruleComputer!!.computer(ClassExportRuleKeys.METHOD_RETURN, method)
        if (returnTypeByRule.notNullOrBlank()) {
            val resolvedReturnType = duckTypeHelper!!.resolve(returnTypeByRule!!.trim(), method.psi())
            if (resolvedReturnType != null) {
                returnType = resolvedReturnType
                fromRule = true
            }
        }
        if (!fromRule) {
            returnType = method.getReturnType()
        }

        if (returnType != null) {
            try {
                val response = Response()

                requestHelper!!.setResponseCode(response, 200)

                val typedResponse = parseResponseBody(returnType, fromRule, method)

                val descOfReturn = docHelper!!.findDocByTag(method.psi(), "return")
                if (descOfReturn.notNullOrBlank()) {
                    val methodReturnMain = ruleComputer.computer(ClassExportRuleKeys.METHOD_RETURN_MAIN, method)
                    if (methodReturnMain.isNullOrBlank()) {
                        requestHelper.appendResponseBodyDesc(response, descOfReturn)
                    } else {
                        val options: ArrayList<HashMap<String, Any?>> = ArrayList()
                        val comment = linkExtractor!!.extract(descOfReturn, method.psi(), object : AbstractLinkResolve() {

                            override fun linkToPsiElement(plainText: String, linkTo: Any?): String? {

                                psiClassHelper!!.resolveEnumOrStatic(plainText, method.psi(), "")
                                        ?.let { options.addAll(it) }

                                return super.linkToPsiElement(plainText, linkTo)
                            }

                            override fun linkToType(plainText: String, linkType: PsiType): String? {
                                return jvmClassHelper!!.resolveClassInType(linkType)?.let {
                                    linkResolver!!.linkToClass(it)
                                }
                            }

                            override fun linkToClass(plainText: String, linkClass: PsiClass): String? {
                                return linkResolver!!.linkToClass(linkClass)
                            }

                            override fun linkToField(plainText: String, linkField: PsiField): String? {
                                return linkResolver!!.linkToProperty(linkField)
                            }

                            override fun linkToMethod(plainText: String, linkMethod: PsiMethod): String? {
                                return linkResolver!!.linkToMethod(linkMethod)
                            }

                            override fun linkToUnresolved(plainText: String): String? {
                                return plainText
                            }
                        })

                        if (comment.notNullOrBlank()) {
                            if (!KVUtils.addKeyComment(typedResponse, methodReturnMain, comment!!)) {
                                requestHelper.appendResponseBodyDesc(response, comment)
                            }
                        }
                        if (options.notNullOrEmpty()) {
                            if (!KVUtils.addKeyOptions(typedResponse, methodReturnMain, options)) {
                                requestHelper.appendResponseBodyDesc(response, KVUtils.getOptionDesc(options))
                            }
                        }
                    }
                }

                requestHelper.setResponseBody(response, "raw", typedResponse)

                requestHelper.addResponseHeader(response, "content-type", "application/json;charset=UTF-8")

                requestHelper.addResponse(request, response)

            } catch (e: ProcessCanceledException) {
                //ignore cancel
            } catch (e: Throwable) {
                logger!!.traceError("error to parse body", e)

            }
        }
    }

    /**
     * unbox queryParam
     */
    protected fun tinyQueryParam(paramVal: String?): String? {
        if (paramVal == null) return null
        var pv = paramVal.trim()
        while (pv.startsWith("[") && pv.endsWith("]")) {
            pv = pv.removeSurrounding("[", "]")
        }
        return pv
    }

    @Deprecated(message = "will be removed soon")
    open protected fun findAttrOfMethod(method: PsiMethod): String? {
        return docHelper!!.getAttrOfDocComment(method)
    }

    private fun extractParamComment(psiMethod: PsiMethod): KV<String, Any>? {
        val subTagMap = docHelper!!.getSubTagMapOfDocComment(psiMethod, "param")

        var methodParamComment: KV<String, Any>? = null
        subTagMap.entries.forEach { entry ->
            val name: String = entry.key
            val value: String? = entry.value
            if (methodParamComment == null) methodParamComment = KV.create()
            if (value.notNullOrBlank()) {

                val options: ArrayList<HashMap<String, Any?>> = ArrayList()
                val comment = linkExtractor!!.extract(value, psiMethod, object : AbstractLinkResolve() {

                    override fun linkToPsiElement(plainText: String, linkTo: Any?): String? {

                        psiClassHelper!!.resolveEnumOrStatic(plainText, psiMethod, name)
                                ?.let { options.addAll(it) }

                        return super.linkToPsiElement(plainText, linkTo)
                    }

                    override fun linkToClass(plainText: String, linkClass: PsiClass): String? {
                        return linkResolver!!.linkToClass(linkClass)
                    }

                    override fun linkToType(plainText: String, linkType: PsiType): String? {
                        return jvmClassHelper!!.resolveClassInType(linkType)?.let {
                            linkResolver!!.linkToClass(it)
                        }
                    }

                    override fun linkToField(plainText: String, linkField: PsiField): String? {
                        return linkResolver!!.linkToProperty(linkField)
                    }

                    override fun linkToMethod(plainText: String, linkMethod: PsiMethod): String? {
                        return linkResolver!!.linkToMethod(linkMethod)
                    }

                    override fun linkToUnresolved(plainText: String): String? {
                        return plainText
                    }
                })

                methodParamComment!![name] = comment ?: ""
                if (options.notNullOrEmpty()) {
                    methodParamComment!!["$name@options"] = options
                }
            }

        }
        return methodParamComment
    }

    private fun foreachMethod(cls: PsiClass, handle: (ExplicitMethod) -> Unit) {
        duckTypeHelper!!.explicit(cls)
                .methods()
                .stream()
                .filter { !jvmClassHelper!!.isBasicMethod(it.psi().name) }
                .filter { !it.psi().hasModifier(JvmModifier.STATIC) }
                .filter { !it.psi().isConstructor }
                .filter { !shouldIgnore(it) }
                .forEach(handle)
    }


    private fun processMethodParameters(method: ExplicitMethod, request: Request) {

        val params = method.getParameters()

        if (params.isNotEmpty()) {

            val paramDocComment = extractParamComment(method.psi())

            val parsedParams: ArrayList<Pair<ExplicitParameter, Any?>> = ArrayList()
            for (param in params) {
                if (ruleComputer!!.computer(ClassExportRuleKeys.PARAM_IGNORE, param) == true) {
                    continue
                }

                val paramType = param.getType() ?: continue
                val unboxType = paramType.unbox()

                if (jvmClassHelper!!.isInheritor(unboxType, *SpringClassName.SPRING_REQUEST_RESPONSE)) {
                    //ignore @HttpServletRequest and @HttpServletResponse

                    continue
                }

                parsedParams.add(param to psiClassHelper!!.getTypeObject(unboxType, param.psi(), JsonOption.READ_COMMENT))
            }

            val hasFile = parsedParams.any { it.second.hasFile() }

            if (hasFile) {
                if (request.method == HttpMethod.GET) {
                    logger?.warn("file param in `GET` API [${request.path}]")
                } else if (request.method == null || request.method == HttpMethod.NO_METHOD) {
                    request.method = ruleComputer!!.computer(ClassExportRuleKeys.METHOD_DEFAULT_HTTP_METHOD,
                            method) ?: HttpMethod.POST
                }
                requestHelper!!.addHeader(request, "Content-Type", "multipart/form-data")
            }

            for (parsedParam in parsedParams) {
                val param = parsedParam.first
                val typeObject = parsedParam.second

                processMethodParameter(
                        request,
                        param,
                        typeObject,
                        KVUtils.getUltimateComment(paramDocComment, param.psi().name).append(readParamDoc(param))
                )
            }
        }

        if (request.method == null || request.method == HttpMethod.NO_METHOD) {
            val defaultHttpMethod = ruleComputer!!.computer(ClassExportRuleKeys.METHOD_DEFAULT_HTTP_METHOD,
                    method)
            requestHelper!!.setMethod(request, defaultHttpMethod ?: HttpMethod.GET)
        }

        if (request.hasBody()) {
            requestHelper!!.addHeaderIfMissed(request, "Content-Type", "application/x-www-form-urlencoded")
        }

    }

    abstract fun processMethodParameter(request: Request, param: ExplicitParameter, typeObject: Any?, paramDesc: String?)

    @Suppress("UNCHECKED_CAST")
    protected fun addParamAsQuery(parameter: ExplicitParameter, typeObject: Any?, request: Request, paramDesc: String? = null) {

        try {
            if (typeObject == Magics.FILE_STR) {
                requestHelper!!.addFormFileParam(
                        request, parameter.name(),
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, paramDesc
                )
            } else if (typeObject != null && typeObject is Map<*, *>) {
                if (request.hasBody() && formExpanded() && typeObject.isComplex()
                        && requestHelper!!.addHeaderIfMissed(request, "Content-Type", "multipart/form-data")) {
                    typeObject.flatValid(object : FieldConsumer {
                        override fun consume(parent: Map<*, *>?, path: String, key: String, value: Any?) {
                            val fv = deepComponent(value)
                            if (fv == Magics.FILE_STR) {
                                requestHelper.addFormFileParam(
                                        request, path,
                                        parent?.getAs<Boolean>(Attrs.REQUIRED_ATTR, key) ?: false,
                                        KVUtils.getUltimateComment(parent?.getAs(Attrs.COMMENT_ATTR), key)
                                )
                            } else {
                                requestHelper.addFormParam(
                                        request, path, tinyQueryParam(value.toString()),
                                        parent?.getAs<Boolean>(Attrs.REQUIRED_ATTR, key) ?: false,
                                        KVUtils.getUltimateComment(parent?.getAs(Attrs.COMMENT_ATTR), key)
                                )
                            }
                        }
                    })
                } else {
                    val fields = typeObject as KV<String, Any?>
                    val comment = fields.getAsKv(Attrs.COMMENT_ATTR)
                    val required = fields.getAsKv(Attrs.REQUIRED_ATTR)
                    fields.forEachValid { filedName, fieldVal ->
                        val fv = deepComponent(fieldVal)
                        if (fv == Magics.FILE_STR) {
                            if (request.method == HttpMethod.GET) {
                                logger!!.warn("try upload file at `GET:`${request.path}")
                            }
                            requestHelper!!.addFormFileParam(
                                    request, filedName,
                                    required?.getAs(filedName) ?: false,
                                    KVUtils.getUltimateComment(comment, filedName)
                            )
                        } else {
                            requestHelper!!.addParam(
                                    request, filedName, null,
                                    required?.getAs(filedName) ?: false,
                                    KVUtils.getUltimateComment(comment, filedName)
                            )
                        }
                    }
                }
            } else {
                requestHelper!!.addParam(
                        request, parameter.name(), tinyQueryParam(typeObject?.toString()),
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, paramDesc
                )
            }
        } catch (e: Exception) {
            logger!!.traceError("error to parse[" + parameter.getType()?.canonicalText() + "] as Querys", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun addParamAsForm(parameter: ExplicitParameter, request: Request, typeObject: Any?, paramDesc: String? = null) {

        try {
            if (typeObject == Magics.FILE_STR) {
                requestHelper!!.addFormFileParam(
                        request, parameter.name(),
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, paramDesc
                )
            } else if (typeObject != null && typeObject is Map<*, *>) {
                if (formExpanded() && typeObject.isComplex()
                        && requestHelper!!.addHeaderIfMissed(request, "Content-Type", "multipart/form-data")) {
                    typeObject.flatValid(object : FieldConsumer {
                        override fun consume(parent: Map<*, *>?, path: String, key: String, value: Any?) {
                            val fv = deepComponent(value)
                            if (fv == Magics.FILE_STR) {
                                requestHelper.addFormFileParam(
                                        request, path,
                                        parent?.getAs<Boolean>(Attrs.REQUIRED_ATTR, key) ?: false,
                                        KVUtils.getUltimateComment(parent?.getAs(Attrs.COMMENT_ATTR), key)
                                )
                            } else {
                                requestHelper.addFormParam(
                                        request, path, tinyQueryParam(value.toString()),
                                        parent?.getAs<Boolean>(Attrs.REQUIRED_ATTR, key) ?: false,
                                        KVUtils.getUltimateComment(parent?.getAs(Attrs.COMMENT_ATTR), key)
                                )
                            }
                        }
                    })
                } else {
                    val fields = typeObject.asKV()
                    val comment = fields.getAsKv(Attrs.COMMENT_ATTR)
                    val required = fields.getAsKv(Attrs.REQUIRED_ATTR)
                    requestHelper!!.addHeaderIfMissed(request, "Content-Type", "application/x-www-form-urlencoded")
                    fields.forEachValid { filedName, fieldVal ->
                        val fv = deepComponent(fieldVal)
                        if (fv == Magics.FILE_STR) {
                            requestHelper.addFormFileParam(
                                    request, filedName,
                                    required?.getAs(filedName) ?: false,
                                    KVUtils.getUltimateComment(comment, filedName)
                            )
                        } else {
                            requestHelper.addFormParam(
                                    request, filedName, null,
                                    required?.getAs(filedName) ?: false,
                                    KVUtils.getUltimateComment(comment, filedName)
                            )
                        }
                    }
                }
            } else {
                requestHelper!!.addFormParam(
                        request, parameter.name(), tinyQueryParam(typeObject?.toString()),
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, paramDesc
                )
            }
        } catch (e: Exception) {
            logger!!.traceError("error to parse[" + parameter.getType()?.canonicalText() + "] as ModelAttribute", e)
        }
    }

    protected fun parseResponseBody(psiType: DuckType?, fromRule: Boolean, method: ExplicitMethod): Any? {

        if (psiType == null) {
            return null
        }

        return when {
            fromRule -> psiClassHelper!!.getTypeObject(psiType, method.psi(), JsonOption.READ_COMMENT)
            needInfer() && (!duckTypeHelper!!.isQualified(psiType) ||
                    jvmClassHelper!!.isInterface(psiType)) -> {
                logger!!.info("try infer return type of method[" + PsiClassUtils.fullNameOfMethod(method.psi()) + "]")
                methodReturnInferHelper!!.inferReturn(method.psi())
//                actionContext!!.callWithTimeout(20000) { methodReturnInferHelper.inferReturn(method) }
            }
            readGetter() -> psiClassHelper!!.getTypeObject(psiType, method.psi(), JsonOption.ALL)
            else -> psiClassHelper!!.getTypeObject(psiType, method.psi(), JsonOption.READ_COMMENT)
        }
    }

    private fun deepComponent(obj: Any?): Any? {
        if (obj == null) {
            return null
        }
        if (obj is Array<*>) {
            if (obj.isEmpty()) return obj
            return deepComponent(obj[0])
        }
        if (obj is Collection<*>) {
            if (obj.isEmpty()) return obj
            return deepComponent(obj.first())
        }
        return obj
    }

    private fun readGetter(): Boolean {
        return settingBinder!!.read().readGetter
    }

    private fun needInfer(): Boolean {
        return settingBinder!!.read().inferEnable
    }

    protected fun formExpanded(): Boolean {
        return settingBinder!!.read().formExpanded
    }
}
