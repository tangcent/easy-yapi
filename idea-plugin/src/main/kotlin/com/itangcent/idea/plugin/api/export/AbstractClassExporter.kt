package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.intellij.util.containers.isNullOrEmpty
import com.itangcent.common.constant.Attrs
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.exception.ProcessCanceledException
import com.itangcent.common.exporter.*
import com.itangcent.common.model.Request
import com.itangcent.common.model.Response
import com.itangcent.common.utils.KVUtils
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.api.MethodReturnInferHelper
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.DuckTypeHelper
import com.itangcent.intellij.psi.JsonOption
import com.itangcent.intellij.psi.PsiClassHelper
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.*
import org.apache.commons.lang3.StringUtils
import java.util.*
import java.util.regex.Pattern

abstract class AbstractClassExporter : ClassExporter, Worker {

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
    protected val psiClassHelper: PsiClassHelper? = null

    @Inject
    protected val docParseHelper: DocParseHelper? = null

    @Inject
    protected val settingBinder: SettingBinder? = null

    @Inject
    protected val duckTypeHelper: DuckTypeHelper? = null

    @Inject
    protected val methodReturnInferHelper: MethodReturnInferHelper? = null

    @Inject
    protected val ruleComputer: RuleComputer? = null

    @Inject(optional = true)
    protected val methodFilter: MethodFilter? = null

    @Inject
    protected var actionContext: ActionContext? = null

    override fun export(cls: Any, requestHelper: RequestHelper, requestHandle: RequestHandle) {
        if (cls !is PsiClass) return
        actionContext!!.checkStatus()
        statusRecorder.newWork()
        try {
            when {
                !hasApi(cls) -> return
                shouldIgnore(cls) -> {
                    logger!!.info("ignore class:" + cls.qualifiedName)
                    return
                }
                else -> {
                    logger!!.info("search api from:${cls.qualifiedName}")

                    val kv = KV.create<String, Any?>()

                    processClass(cls, kv)

                    foreachMethod(cls) { method ->
                        if (isApi(method) && methodFilter?.checkMethod(method) != false) {
                            exportMethodApi(method, kv, requestHelper, requestHandle)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger!!.traceError(e)
        } finally {
            statusRecorder.endWork()
        }
    }

    protected abstract fun processClass(cls: PsiClass, kv: KV<String, Any?>)

    protected abstract fun hasApi(psiClass: PsiClass): Boolean

    protected abstract fun isApi(psiMethod: PsiMethod): Boolean

    open protected fun shouldIgnore(psiElement: PsiElement): Boolean {
        return ruleComputer!!.computer(ClassExportRuleKeys.IGNORE, psiElement) ?: false
    }

    private fun exportMethodApi(method: PsiMethod, kv: KV<String, Any?>
                                , requestHelper: RequestHelper,
                                requestHandle: RequestHandle) {

        actionContext!!.checkStatus()

        val request = Request()

        request.resource = method

        processMethod(method, kv, request, requestHelper)

        processMethodParameters(method, request, requestHelper)

        processResponse(method, request, requestHelper)

        processCompleted(method, request, requestHelper)

        requestHandle(request)
    }

    protected open fun processMethod(method: PsiMethod, kv: KV<String, Any?>, request: Request, requestHelper: RequestHelper) {

        val attr: String?
        var attrOfMethod = findAttrOfMethod(method, requestHelper)
        attrOfMethod = docParseHelper!!.resolveLinkInAttr(attrOfMethod, method, requestHelper)

        if (attrOfMethod.isNullOrBlank()) {
            requestHelper.setName(request, method.name)
        } else {
            val lines = attrOfMethod.lines()
            attr = if (lines.size > 1) {//multi line
                lines.firstOrNull { it.isNotBlank() }
            } else {
                attrOfMethod
            }

            requestHelper.appendDesc(request, attrOfMethod)
            requestHelper.setName(request, attr ?: method.name)
        }

        readMethodDoc(method)?.let {
            requestHelper.appendDesc(request, docParseHelper.resolveLinkInAttr(it, method, requestHelper))
        }

    }

    protected open fun readMethodDoc(method: PsiMethod): String? {
        return ruleComputer!!.computer(ClassExportRuleKeys.METHOD_DOC, method)
    }

    protected open fun processCompleted(method: PsiMethod, request: Request, requestHelper: RequestHelper) {
        //call after process
    }

    protected open fun processResponse(method: PsiMethod, request: Request, requestHelper: RequestHelper) {

        val returnType = method.returnType
        if (returnType != null) {
            try {
                val response = Response()

                requestHelper.setResponseCode(response, 200)

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
        if (pathPre == null) return pathAfter
        if (pathAfter == null) return pathPre
        return pathPre.removeSuffix("/") + "/" + pathAfter.removePrefix("/")
    }

    open protected fun findAttrOfMethod(method: PsiMethod, requestHelper: RequestHelper): String? {
        return DocCommentUtils.getAttrOfDocComment(method.docComment)
    }

    @Suppress("UNCHECKED_CAST")
    protected open fun findAttrForParam(paramName: String?, docComment: KV<String, Any>?): String? {
        return when {
            paramName == null -> null
            docComment == null -> null
            docComment.containsKey("$paramName@options") -> {
                val options = docComment["$paramName@options"] as List<Map<String, Any?>>
                "${docComment[paramName]}${KVUtils.getOptionDesc(options)}"
            }
            else -> docComment[paramName] as String?
        }
    }

    private fun extractParamComment(psiMethod: PsiMethod): KV<String, Any>? {
        val docComment = psiMethod.docComment
        var methodParamComment: KV<String, Any>? = null
        if (docComment != null) {
            for (paramDocTag in docComment.findTagsByName("param")) {
                var name: String? = null
                var value: String? = null
                paramDocTag.dataElements
                        .asSequence()
                        .map { it?.text }
                        .filterNot { StringUtils.isBlank(it) }
                        .forEach {
                            when {
                                name == null -> name = it
                                value == null -> value = it
                                else -> value += it
                            }
                        }
                if (StringUtils.isNoneBlank(name, value)) {
                    if (methodParamComment == null) methodParamComment = KV.create()

                    if (value!!.contains("@link")) {
                        val pattern = Pattern.compile("\\{@link (.*?)\\}")
                        val matcher = pattern.matcher(value)

                        val options: ArrayList<HashMap<String, Any?>> = ArrayList()

                        val sb = StringBuffer()
                        while (matcher.find()) {
                            matcher.appendReplacement(sb, "")
                            val linkClassOrProperty = matcher.group(1)
                            psiClassHelper!!.resolveEnumOrStatic(linkClassOrProperty, psiMethod, name!!)
                                    ?.let { options.addAll(it) }
                        }
                        matcher.appendTail(sb)
                        methodParamComment[name!!] = sb.toString()
                        if (!options.isNullOrEmpty()) {
                            methodParamComment["$name@options"] = options
                        }
                        continue
                    }
                    methodParamComment[name!!] = value!!
                }
            }
        }
        return methodParamComment
    }

    private fun foreachMethod(cls: PsiClass, handle: (PsiMethod) -> Unit) {
        cls.allMethods
                .filter { !PsiClassHelper.JAVA_OBJECT_METHODS.contains(it.name) }
                .filter { !it.hasModifier(JvmModifier.STATIC) }
                .filter { !shouldIgnore(it) }
                .forEach(handle)
    }

    private fun processMethodParameters(method: PsiMethod, request: Request,
                                        requestHelper: RequestHelper) {

        val params = method.parameterList.parameters

        if (params.isNotEmpty()) {

            val paramDocComment = extractParamComment(method)

            for (param in params) {
                processMethodParameter(method, request, param, paramDocComment?.get(param.name!!)?.toString(), requestHelper)
            }
        }

        //default to GET
        if (request.method == null || request.method == HttpMethod.NO_METHOD) {
            requestHelper.setMethod(request, HttpMethod.GET)
        }
    }

    abstract fun processMethodParameter(method: PsiMethod, request: Request, param: PsiParameter, paramDesc: String?, requestHelper: RequestHelper)

    @Suppress("UNCHECKED_CAST")
    protected fun addParamAsQuery(parameter: PsiParameter, request: Request, requestHelper: RequestHelper, paramDesc: String? = null) {
        val paramType = parameter.type
        try {
            val unboxType = psiClassHelper!!.unboxArrayOrList(paramType)
            val typeObject = psiClassHelper.getTypeObject(unboxType, parameter, JsonOption.READ_COMMENT)
            if (typeObject != null && typeObject is KV<*, *>) {
                val fields = typeObject as KV<String, Any>
                val comment: KV<String, Any>? = fields.getAs(Attrs.COMMENT_ATTR)
                val required: KV<String, Any>? = fields.getAs(Attrs.REQUIRED_ATTR)
                fields.forEachValid { filedName, fieldVal ->
                    requestHelper.addParam(request, filedName, tinyQueryParam(fieldVal.toString()),
                            required?.getAs(filedName) ?: false,
                            KVUtils.getUltimateComment(comment, filedName))
                }
            } else if (typeObject == Magics.FILE_STR) {
                requestHelper.addHeader(request, "Content-Type", "multipart/form-data")
                requestHelper.addFormFileParam(request, parameter.name!!,
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, paramDesc)
            } else {
                requestHelper.addParam(request, parameter.name!!, tinyQueryParam(typeObject?.toString()),
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, paramDesc)
            }
        } catch (e: Exception) {
            logger!!.error("error to parse[" + paramType.canonicalText + "] as ModelAttribute")
            logger.traceError(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun addParamAsForm(parameter: PsiParameter, request: Request, requestHelper: RequestHelper, paramDesc: String? = null) {

        val paramType = parameter.type
        try {
            val unboxType = psiClassHelper!!.unboxArrayOrList(paramType)
            val typeObject = psiClassHelper.getTypeObject(unboxType, parameter, JsonOption.READ_COMMENT)
            if (typeObject != null && typeObject is KV<*, *>) {
                val fields = typeObject as KV<String, Any>
                val comment: KV<String, Any>? = fields.getAs(Attrs.COMMENT_ATTR)
                val required: KV<String, Any>? = fields.getAs(Attrs.REQUIRED_ATTR)
                requestHelper.addHeader(request, "Content-Type", "application/x-www-form-urlencoded")
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
                requestHelper.addHeader(request, "Content-Type", "multipart/form-data")
                requestHelper.addFormFileParam(request, parameter.name!!,
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, paramDesc)
            } else {
                requestHelper.addParam(request, parameter.name!!, tinyQueryParam(typeObject?.toString()),
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
                    PsiHelper.isInterface(psiType)) -> {
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
        return settingBinder!!.read().readGetter ?: false
    }

    private fun needInfer(): Boolean {
        return settingBinder!!.read().inferEnable ?: false
    }

    private fun inferMaxDeep(): Int {
        return settingBinder!!.read().inferMaxDeep ?: 4
    }

}