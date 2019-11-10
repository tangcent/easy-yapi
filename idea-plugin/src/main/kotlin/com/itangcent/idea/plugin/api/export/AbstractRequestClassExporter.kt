package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.intellij.util.containers.isNullOrEmpty
import com.itangcent.common.constant.Attrs
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.exception.ProcessCanceledException
import com.itangcent.common.model.Header
import com.itangcent.common.model.Param
import com.itangcent.common.model.Request
import com.itangcent.common.model.Response
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.KV
import com.itangcent.common.utils.KVUtils
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.api.MethodReturnInferHelper
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.*
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.JsonOption
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.Magics
import com.itangcent.intellij.util.forEachValid
import com.itangcent.intellij.util.traceError
import java.util.*
import kotlin.reflect.KClass

abstract class AbstractRequestClassExporter : ClassExporter, Worker {

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
    protected val docParseHelper: DocParseHelper? = null

    @Inject
    protected val requestHelper: RequestHelper? = null

    @Inject
    protected val settingBinder: SettingBinder? = null

    @Inject
    protected val duckTypeHelper: DuckTypeHelper? = null

    @Inject
    protected val methodReturnInferHelper: MethodReturnInferHelper? = null

    @Inject
    protected val ruleComputer: RuleComputer? = null

    @Inject
    protected val jvmClassHelper: JvmClassHelper? = null

    @Inject(optional = true)
    protected val methodFilter: MethodFilter? = null

    @Inject
    protected var actionContext: ActionContext? = null

    @Inject
    private val linkExtractor: LinkExtractor? = null

    @Inject
    private val linkResolver: LinkResolver? = null

    override fun export(cls: Any, docHandle: DocHandle): Boolean {
        if (cls !is PsiClass) return false
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

                    foreachMethod(cls) { method ->
                        if (isApi(method) && methodFilter?.checkMethod(method) != false) {
                            exportMethodApi(cls, method, kv, docHandle)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger!!.traceError(e)
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

    private fun exportMethodApi(psiClass: PsiClass, method: PsiMethod, kv: KV<String, Any?>,
                                docHandle: DocHandle) {

        actionContext!!.checkStatus()

        val request = Request()

        request.resource = PsiMethodResource(method, psiClass)

        processMethod(method, kv, request)

        processMethodParameters(method, request)

        processResponse(method, request)

        processCompleted(method, request)

        docHandle(request)
    }

    protected open fun processMethod(method: PsiMethod, kv: KV<String, Any?>, request: Request) {

        val attr: String?
        var attrOfMethod = findAttrOfMethod(method)
        attrOfMethod = docParseHelper!!.resolveLinkInAttr(attrOfMethod, method)

        if (attrOfMethod.isNullOrBlank()) {
            requestHelper!!.setName(request, method.name)
        } else {
            val lines = attrOfMethod.lines()
            attr = if (lines.size > 1) {//multi line
                lines.firstOrNull { it.isNotBlank() }
            } else {
                attrOfMethod
            }

            requestHelper!!.appendDesc(request, attrOfMethod)
            requestHelper.setName(request, attr ?: method.name)
        }

        readMethodDoc(method)?.let {
            requestHelper.appendDesc(request, docParseHelper.resolveLinkInAttr(it, method))
        }

    }

    protected open fun readMethodDoc(method: PsiMethod): String? {
        return ruleComputer!!.computer(ClassExportRuleKeys.METHOD_DOC, method)
    }

    protected open fun processCompleted(method: PsiMethod, request: Request) {
        //parse additionalHeader by config
        val additionalHeader = ruleComputer!!.computer(ClassExportRuleKeys.METHOD_ADDITIONAL_HEADER, method)
        if (!additionalHeader.isNullOrEmpty()) {
            val additionalHeaders = additionalHeader.lines()
            for (headerStr in additionalHeaders) {
                val header = GsonUtils.fromJson(headerStr, Header::class)
                if (header.name.isNullOrBlank()) {
                    logger!!.warn("no name had be found in:$headerStr")
                    continue
                }
                requestHelper!!.addHeader(request, header)
            }
        }

        //parse additionalParam by config
        val additionalParam = ruleComputer.computer(ClassExportRuleKeys.METHOD_ADDITIONAL_PARAM, method)
        if (!additionalParam.isNullOrEmpty()) {
            val additionalParams = additionalParam.lines()
            for (paramStr in additionalParams) {
                val param = GsonUtils.fromJson(paramStr, Param::class)
                if (param.name.isNullOrBlank()) {
                    logger!!.warn("no name had be found in:$paramStr")
                    continue
                }
                requestHelper!!.addParam(request, param)
            }
        }

        //parse additionalResponseHeader by config
        if (!request.response.isNullOrEmpty()) {
            val additionalResponseHeader = ruleComputer.computer(ClassExportRuleKeys.METHOD_ADDITIONAL_RESPONSE_HEADER, method)
            if (!additionalResponseHeader.isNullOrEmpty()) {
                val additionalHeaders = additionalResponseHeader.lines()
                for (headerStr in additionalHeaders) {
                    val header = GsonUtils.fromJson(headerStr, Header::class)
                    if (header.name.isNullOrBlank()) {
                        logger!!.warn("no name had be found in:$headerStr")
                        continue
                    }
                    request.response!!.forEach {
                        requestHelper!!.addResponseHeader(it, header)
                    }
                }
            }
        }
    }

    protected open fun processResponse(method: PsiMethod, request: Request) {

        val returnType = method.returnType
        if (returnType != null) {
            try {
                val response = Response()

                requestHelper!!.setResponseCode(response, 200)

                val typedResponse = parseResponseBody(returnType, method)

                requestHelper.setResponseBody(response, "raw", typedResponse)

                requestHelper.addResponseHeader(response, "content-type", "application/json;charset=UTF-8")

                requestHelper.addResponse(request, response)

            } catch (e: ProcessCanceledException) {
                //ignore cancel
            } catch (e: Throwable) {
                logger!!.error("error to parse body")
                logger.traceError(e)
            }
        }
    }

    /**
     * unbox queryParam
     */
    protected fun tinyQueryParam(paramVal: String?): String? {
        if (paramVal == null) return null
        var pv = paramVal.trim()
        while (true) {
            if (pv.startsWith("[")) {
                pv = pv.trim('[', ']')
                continue
            }
            break
        }
        return pv
    }

    protected fun contractPath(pathPre: String?, pathAfter: String?): String? {
        if (pathPre.isNullOrBlank()) return pathAfter
        if (pathAfter.isNullOrBlank()) return pathPre
        return pathPre.removeSuffix("/") + "/" + pathAfter.removePrefix("/")
    }

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
            if (!value.isNullOrBlank()) {

                val options: ArrayList<HashMap<String, Any?>> = ArrayList()
                val comment = linkExtractor!!.extract(value, psiMethod, object : AbstractLinkResolve() {

                    override fun linkToPsiElement(plainText: String, linkTo: PsiElement?): String? {

                        psiClassHelper!!.resolveEnumOrStatic(plainText, psiMethod, name)
                                ?.let { options.addAll(it) }

                        return super.linkToPsiElement(plainText, linkTo)
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

                methodParamComment!![name] = comment ?: ""
                if (!options.isNullOrEmpty()) {
                    methodParamComment!!["$name@options"] = options
                }
            }

        }
        return methodParamComment
    }

    private fun foreachMethod(cls: PsiClass, handle: (PsiMethod) -> Unit) {
        cls.allMethods
                .filter { !jvmClassHelper!!.isBasicMethod(it.name) }
                .filter { !it.hasModifier(JvmModifier.STATIC) }
                .filter { !it.isConstructor }
                .filter { !shouldIgnore(it) }
                .forEach(handle)
    }

    private fun processMethodParameters(method: PsiMethod, request: Request) {

        val params = method.parameterList.parameters

        if (params.isNotEmpty()) {

            val paramDocComment = extractParamComment(method)

            for (param in params) {
                if (ruleComputer!!.computer(ClassExportRuleKeys.PARAM_IGNORE, param) == true) {
                    continue
                }
                processMethodParameter(method, request, param, KVUtils.getUltimateComment(paramDocComment, param.name))
            }
        }

        //default to GET
        if (request.method == null || request.method == HttpMethod.NO_METHOD) {
            requestHelper!!.setMethod(request, HttpMethod.GET)
        }
    }

    abstract fun processMethodParameter(method: PsiMethod, request: Request, param: PsiParameter, paramDesc: String?)

    @Suppress("UNCHECKED_CAST")
    protected fun addParamAsQuery(parameter: PsiParameter, request: Request, paramDesc: String? = null) {
        val paramType = parameter.type
        try {
            val unboxType = psiClassHelper!!.unboxArrayOrList(paramType)
            val typeObject = psiClassHelper.getTypeObject(unboxType, parameter, JsonOption.READ_COMMENT)
            if (typeObject != null && typeObject is KV<*, *>) {
                val fields = typeObject as KV<String, Any>
                val comment: KV<String, Any>? = fields.getAs(Attrs.COMMENT_ATTR)
                val required: KV<String, Any>? = fields.getAs(Attrs.REQUIRED_ATTR)
                fields.forEachValid { filedName, fieldVal ->
                    requestHelper!!.addParam(request, filedName, tinyQueryParam(fieldVal.toString()),
                            required?.getAs(filedName) ?: false,
                            KVUtils.getUltimateComment(comment, filedName))
                }
            } else if (typeObject == Magics.FILE_STR) {
                requestHelper!!.addHeader(request, "Content-Type", "multipart/form-data")
                requestHelper.addFormFileParam(request, parameter.name!!,
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, paramDesc)
            } else {
                requestHelper!!.addParam(request, parameter.name!!, tinyQueryParam(typeObject?.toString()),
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, paramDesc)
            }
        } catch (e: Exception) {
            logger!!.error("error to parse[" + paramType.canonicalText + "] as ModelAttribute")
            logger.traceError(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun addParamAsForm(parameter: PsiParameter, request: Request, paramDesc: String? = null) {

        val paramType = parameter.type
        try {
            val unboxType = psiClassHelper!!.unboxArrayOrList(paramType)
            val typeObject = psiClassHelper.getTypeObject(unboxType, parameter, JsonOption.READ_COMMENT)
            if (typeObject != null && typeObject is KV<*, *>) {
                val fields = typeObject as KV<String, Any>
                val comment: KV<String, Any>? = fields.getAs(Attrs.COMMENT_ATTR)
                val required: KV<String, Any>? = fields.getAs(Attrs.REQUIRED_ATTR)
                requestHelper!!.addHeader(request, "Content-Type", "application/x-www-form-urlencoded")
                fields.forEachValid { filedName, fieldVal ->
                    val fv = deepComponent(fieldVal)
                    if (fv == Magics.FILE_STR) {
                        requestHelper.addHeader(request, "Content-Type", "multipart/form-data")
                        requestHelper.addFormFileParam(request, filedName,
                                required?.getAs(filedName) ?: false,
                                KVUtils.getUltimateComment(comment, filedName))
                    } else {
                        requestHelper.addFormParam(request, filedName, null,
                                required?.getAs(filedName) ?: false,
                                KVUtils.getUltimateComment(comment, filedName))
                    }
                }
            } else if (typeObject == Magics.FILE_STR) {
                requestHelper!!.addHeader(request, "Content-Type", "multipart/form-data")
                requestHelper.addFormFileParam(request, parameter.name!!,
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, paramDesc)
            } else {
                requestHelper!!.addParam(request, parameter.name!!, tinyQueryParam(typeObject?.toString()),
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, paramDesc)
            }
        } catch (e: Exception) {
            logger!!.error("error to parse[" + paramType.canonicalText + "] as ModelAttribute")
            logger.traceError(e)
        }
    }

    protected fun parseRequestBody(psiType: PsiType?, context: PsiElement): Any? {
        return psiClassHelper!!.getTypeObject(psiType, context, JsonOption.READ_COMMENT)
    }

    protected fun parseResponseBody(psiType: PsiType?, method: PsiMethod): Any? {

        if (psiType == null) {
            return null
        }

        return when {
            needInfer() && (!duckTypeHelper!!.isQualified(psiType, method) ||
                    PsiClassUtils.isInterface(psiType)) -> {
                methodReturnInferHelper!!.setMaxDeep(inferMaxDeep())
                logger!!.info("try infer return type of method[" + PsiClassUtils.fullNameOfMethod(method) + "]")
                methodReturnInferHelper.inferReturn(method)
//                actionContext!!.callWithTimeout(20000) { methodReturnInferHelper.inferReturn(method) }
            }
            readGetter() -> psiClassHelper!!.getTypeObject(psiType, method, JsonOption.ALL)
            else -> psiClassHelper!!.getTypeObject(psiType, method, JsonOption.READ_COMMENT)
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

    private fun inferMaxDeep(): Int {
        return settingBinder!!.read().inferMaxDeep
    }
}