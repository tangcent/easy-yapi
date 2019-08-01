package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.util.containers.isNullOrEmpty
import com.itangcent.common.constant.Attrs
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.exception.ProcessCanceledException
import com.itangcent.common.exporter.*
import com.itangcent.common.model.Header
import com.itangcent.common.model.Request
import com.itangcent.common.model.RequestHandle
import com.itangcent.common.model.Response
import com.itangcent.common.utils.KVUtils
import com.itangcent.idea.constant.SpringAttrs
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.api.MethodReturnInferHelper
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.utils.SpringClassName
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.*
import com.itangcent.intellij.util.*
import org.apache.commons.lang3.StringUtils
import java.util.*
import java.util.regex.Pattern

class SpringClassExporter : ClassExporter, Worker {

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
    private val logger: Logger? = null

    @Inject
    private val psiClassHelper: PsiClassHelper? = null

    @Inject
    private val docParseHelper: DefaultDocParseHelper? = null

    @Inject
    private val settingBinder: SettingBinder? = null

    @Inject
    private val duckTypeHelper: DuckTypeHelper? = null

    @Inject
    private val methodReturnInferHelper: MethodReturnInferHelper? = null

    @Inject
    private val ruleComputer: RuleComputer? = null

    @Inject(optional = true)
    private val methodFilter: MethodFilter? = null

    @Inject
    private var actionContext: ActionContext? = null

    override fun export(cls: Any, parseHandle: ParseHandle, requestHandle: RequestHandle) {
        if (cls !is PsiClass) return
        actionContext!!.checkStatus()
        statusRecorder.newWork()
        try {
            when {
                !isCtrl(cls) -> return
                shouldIgnore(cls) -> {
                    logger!!.info("ignore class:" + cls.qualifiedName)
                    return
                }
                else -> {
                    logger!!.info("search api from:${cls.qualifiedName}")
                    val ctrlRequestMappingAnn = findRequestMapping(cls)
                    val basePath: String = findHttpPath(ctrlRequestMappingAnn) ?: ""

                    val ctrlHttpMethod = findHttpMethod(ctrlRequestMappingAnn)

                    foreachMethod(cls) { method ->
                        if (methodFilter?.checkMethod(method) != false) {
                            exportMethodApi(method, basePath, ctrlHttpMethod, parseHandle, requestHandle)
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

    private fun isCtrl(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any {
            SpringAttrs.SPRING_CONTROLLER_ANNOTATION.contains(it.qualifiedName)
        }
    }

    private fun shouldIgnore(psiElement: PsiElement): Boolean {
        return ruleComputer!!.computer(ClassExportRuleKeys.IGNORE, psiElement) ?: false
    }

    private fun exportMethodApi(method: PsiMethod, basePath: String, ctrlHttpMethod: String
                                , parseHandle: ParseHandle, requestHandle: RequestHandle) {

        actionContext!!.checkStatus()
        val requestMappingAnn = findRequestMappingInAnn(method) ?: return
        val request = Request()
        request.resource = method

        var httpMethod = findHttpMethod(requestMappingAnn)
        if (httpMethod == HttpMethod.NO_METHOD && ctrlHttpMethod != HttpMethod.NO_METHOD) {
            httpMethod = ctrlHttpMethod
        }

        parseHandle.setMethod(request, httpMethod)
        val httpPath = contractPath(basePath, findHttpPath(requestMappingAnn))!!
        parseHandle.setPath(request, httpPath)

        val attr: String?
        val attrOfMethod = findAttrOfMethod(method, parseHandle)!!
        val lines = attrOfMethod.lines()
        attr = if (lines.size > 1) {//multi line
            lines.firstOrNull { it.isNotBlank() }
        } else {
            attrOfMethod
        }

        parseHandle.appendDesc(request, attrOfMethod)

        findDeprecatedOfMethod(method, parseHandle)?.let {
            parseHandle.appendDesc(request, it)
        }

        readMethodDoc(method, parseHandle)?.let {
            parseHandle.appendDesc(request, it)
        }

        parseHandle.setName(request, attr ?: method.name)

        parseHandle.setMethod(request, httpMethod)

        processMethodParameters(method, request, parseHandle)

        processResponse(method, request, parseHandle)

        requestHandle(request)
    }

    private fun readMethodDoc(method: PsiMethod, parseHandle: ParseHandle): String? {
        return ruleComputer!!.computer(ClassExportRuleKeys.METHOD_DOC, method)?.let { docParseHelper!!.resolveLinkInAttr(it, method, parseHandle) }
    }

    private fun processResponse(method: PsiMethod, request: Request, parseHandle: ParseHandle) {

        val returnType = method.returnType
        if (returnType != null) {
            try {
                val response = Response()

                parseHandle.setResponseCode(response, 200)

                val typedResponse = parseResponseBody(returnType, method)

                parseHandle.setResponseBody(response, "raw", typedResponse)

                parseHandle.addResponseHeader(response, "content-type", "application/json;charset=UTF-8")

                parseHandle.addResponse(request, response)

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
    private fun tinyQueryParam(paramVal: String?): String? {
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

    private fun contractPath(pathPre: String?, pathAfter: String?): String? {
        if (pathPre == null) return pathAfter
        if (pathAfter == null) return pathPre
        return pathPre.removeSuffix("/") + "/" + pathAfter.removePrefix("/")
    }

    private fun findHttpPath(requestMappingAnn: PsiAnnotation?): String? {
        val path = PsiAnnotationUtils.findAttr(requestMappingAnn, "path", "value") ?: return null

        return when {
            path.contains(",") -> PsiAnnotationUtils.tinyAnnStr(path.substringBefore(','))
            else -> path
        }
    }

    private fun findHttpMethod(requestMappingAnn: PsiAnnotation?): String {
        if (requestMappingAnn != null) {
            when {
                requestMappingAnn.qualifiedName == SpringClassName.REQUESTMAPPING_ANNOTATION -> {
                    var method = PsiAnnotationUtils.findAttr(requestMappingAnn, "method") ?: return HttpMethod.NO_METHOD
                    if (method.contains(",")) {
                        method = method.substringBefore(",")
                    }
                    return when {
                        StringUtils.isBlank(method) -> {
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
                requestMappingAnn.qualifiedName == SpringClassName.GET_MAPPING -> return HttpMethod.GET
                requestMappingAnn.qualifiedName == SpringClassName.POST_MAPPING -> return HttpMethod.POST
                requestMappingAnn.qualifiedName == SpringClassName.DELETE_MAPPING -> return HttpMethod.DELETE
                requestMappingAnn.qualifiedName == SpringClassName.PATCH_MAPPING -> return HttpMethod.PATCH
                requestMappingAnn.qualifiedName == SpringClassName.PUT_MAPPING -> return HttpMethod.PUT
            }
        }
        return HttpMethod.NO_METHOD
    }

    private fun findRequestMapping(psiClass: PsiClass): PsiAnnotation? {
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

    private fun findRequestMappingInAnn(ele: PsiModifierListOwner): PsiAnnotation? {
        return SPRING_REQUEST_MAPPING_ANNOTATIONS
                .map { PsiAnnotationUtils.findAnn(ele, it) }
                .firstOrNull { it != null }
    }

    private fun findRequestBody(parameter: PsiParameter): PsiAnnotation? {
        return PsiAnnotationUtils.findAnn(parameter, SpringClassName.REQUESTBOODY_ANNOTATION)
    }

    private fun findModelAttr(parameter: PsiParameter): PsiAnnotation? {
        return PsiAnnotationUtils.findAnn(parameter, SpringClassName.MODELATTRIBUTE_ANNOTATION)
    }

    private fun findRequestHeader(parameter: PsiParameter): PsiAnnotation? {
        return PsiAnnotationUtils.findAnn(parameter, REQUEST_HEADER)
    }

    private fun findPathVariable(parameter: PsiParameter): PsiAnnotation? {
        return PsiAnnotationUtils.findAnn(parameter, SpringClassName.PATHVARIABLE_ANNOTATION)
    }

    private fun findRequestParam(parameter: PsiParameter): PsiAnnotation? {
        return PsiAnnotationUtils.findAnn(parameter, SpringClassName.REQUESTPARAM_ANNOTATION)
    }

    private fun findParamName(requestParamAnn: PsiAnnotation?): String? {
        return PsiAnnotationUtils.findAttr(requestParamAnn, "name", "value")
    }

    private fun findParamRequired(requestParamAnn: PsiAnnotation?): Boolean? {
        val required = PsiAnnotationUtils.findAttr(requestParamAnn, "name", "required") ?: return null
        return when {
            required.contains("false") -> false
            else -> null
        }
    }

    private fun findAttrOfMethod(method: PsiMethod, parseHandle: ParseHandle): String? {
        val docComment = method.docComment

        val docText = DocCommentUtils.getAttrOfDocComment(docComment)
        return when {
            StringUtils.isBlank(docText) -> method.name
            else -> docParseHelper!!.resolveLinkInAttr(docText, method, parseHandle)
        }
    }

    protected fun findDeprecatedOfMethod(method: PsiMethod, parseHandle: ParseHandle): String? {
        return ruleComputer!!.computer(ClassExportRuleKeys.DEPRECATE, method)?.let { docParseHelper!!.resolveLinkInAttr(it, method, parseHandle) }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun findAttrForParam(paramName: String?, docComment: KV<String, Any>?): String? {
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
                                        parseHandle: ParseHandle) {

        val params = method.parameterList.parameters
        var httpMethod = request.method ?: HttpMethod.NO_METHOD
        if (params.isNotEmpty()) {

            val paramDocComment = extractParamComment(method)

            for (param in params) {

                val requestBodyAnn = findRequestBody(param)
                if (requestBodyAnn != null) {
                    if (httpMethod == HttpMethod.NO_METHOD) {
                        httpMethod = HttpMethod.POST
                    }
                    parseHandle.addHeader(request, "Content-Type", "application/json")
                    parseHandle.setJsonBody(
                            request,
                            parseRequestBody(param.type, method),
                            findAttrForParam(param.name, paramDocComment)
                    )
                    continue
                }

                val modelAttrAnn = findModelAttr(param)
                if (modelAttrAnn != null) {
                    if (httpMethod == HttpMethod.GET) {
                        addModelAttrAsQuery(param, request, parseHandle)
                    } else {
                        if (httpMethod == HttpMethod.NO_METHOD) {
                            httpMethod = HttpMethod.POST
                        }

                        addModelAttr(param, request, parseHandle)
                    }
                    continue
                }

                val requestHeaderAnn = findRequestHeader(param)
                if (requestHeaderAnn != null) {
                    val attr = findAttrForParam(param.name, paramDocComment)

                    var headName = PsiAnnotationUtils.findAttr(requestHeaderAnn,
                            "value")
                    if (headName.isNullOrBlank()) {
                        headName = PsiAnnotationUtils.findAttr(requestHeaderAnn,
                                "name")
                    }
                    if (headName.isNullOrBlank()) {
                        headName = param.name
                    }

                    val required = findParamRequired(requestHeaderAnn) ?: true

                    var defaultValue = PsiAnnotationUtils.findAttr(requestHeaderAnn,
                            "defaultValue")

                    if (defaultValue == null
                            || defaultValue == ESCAPE_REQUEST_HEADER_DEFAULT_NONE
                            || defaultValue == REQUEST_HEADER_DEFAULT_NONE) {
                        defaultValue = ""
                    }

                    val header = Header()
                    header.name = headName
                    header.value = defaultValue
                    header.example = defaultValue
                    header.desc = attr
                    header.required = required
                    parseHandle.addHeader(request, header)
                    continue
                }

                val pathVariableAnn = findPathVariable(param)
                if (pathVariableAnn != null) {
                    val attr = findAttrForParam(param.name, paramDocComment)

                    var pathName = PsiAnnotationUtils.findAttr(pathVariableAnn,
                            "value")
                    if (pathName == null) {
                        pathName = param.name
                    }

                    parseHandle.addPathParam(request, pathName!!, attr ?: "")
                    continue
                }

                var paramName: String? = null
                var required: Boolean = false

                val requestParamAnn = findRequestParam(param)
                if (requestParamAnn != null) {
                    paramName = findParamName(requestParamAnn)
                    required = findParamRequired(requestParamAnn) ?: true
                }
                if (!required) {
                    required = ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, param) ?: false
                }
                if (StringUtils.isBlank(paramName)) {
                    paramName = param.name!!
                }

                val paramType = param.type
                val unboxType = psiClassHelper!!.unboxArrayOrList(paramType)
                val paramCls = PsiTypesUtil.getPsiClass(unboxType)
                var defaultVal: Any? = null
                if (unboxType is PsiPrimitiveType) { //primitive Type
                    defaultVal = PsiTypesUtil.getDefaultValue(unboxType)
                } else if (psiClassHelper.isNormalType(unboxType.canonicalText)) {//normal type
                    defaultVal = psiClassHelper.getDefaultValue(unboxType.canonicalText)
                } else if (paramCls != null && ruleComputer!!.computer(ClassRuleKeys.TYPE_IS_FILE, paramCls) == true) {
                    if (httpMethod == HttpMethod.GET) {
                        //can not upload file in a GET method
                        logger!!.error("Couldn't upload file in 'GET':[$httpMethod:${request.path}],param:${param.name} type:{${paramType.canonicalText}}")
                        continue
                    }

                    if (httpMethod == HttpMethod.NO_METHOD) {
                        httpMethod = HttpMethod.POST
                    }

                    parseHandle.addHeader(request, "Content-Type", "multipart/form-data")
                    parseHandle.addFormFileParam(request, paramName!!, required, findAttrForParam(param.name, paramDocComment))
                    continue
                } else if (SpringAttrs.SPRING_REQUEST_RESPONSE.contains(unboxType.presentableText)) {
                    //ignore @HttpServletRequest and @HttpServletResponse
                    continue
                }

                if (defaultVal != null) {
                    parseHandle.addParam(request,
                            paramName!!
                            , defaultVal.toString()
                            , required
                            , findAttrForParam(param.name, paramDocComment))
                } else {
                    if (httpMethod == HttpMethod.GET) {
                        addModelAttrAsQuery(param, request, parseHandle, findAttrForParam(param.name, paramDocComment))
                    } else {
                        if (httpMethod == HttpMethod.NO_METHOD) {
                            httpMethod = HttpMethod.POST
                        }
                        addModelAttr(param, request, parseHandle, findAttrForParam(param.name, paramDocComment))
                    }
                }
            }

        }
        if (httpMethod == HttpMethod.NO_METHOD) {
            httpMethod = HttpMethod.GET
        }
        parseHandle.setMethod(request, httpMethod)
    }

    @Suppress("UNCHECKED_CAST")
    fun addModelAttrAsQuery(parameter: PsiParameter, request: Request, parseHandle: ParseHandle, attrFromParam: String? = null) {
        val paramType = parameter.type
        try {
            val unboxType = psiClassHelper!!.unboxArrayOrList(paramType)
            val typeObject = psiClassHelper.getTypeObject(unboxType, parameter, JsonOption.READ_COMMENT)
            if (typeObject != null && typeObject is KV<*, *>) {
                val fields = typeObject as KV<String, Any>
                val comment: KV<String, Any>? = fields.getAs(Attrs.COMMENT_ATTR)
                val required: KV<String, Any>? = fields.getAs(Attrs.REQUIRED_ATTR)
                fields.forEachValid { filedName, fieldVal ->
                    parseHandle.addParam(request, filedName, tinyQueryParam(fieldVal.toString()),
                            required?.getAs(filedName) ?: false,
                            KVUtils.getUltimateComment(comment, filedName))
                }
            } else if (typeObject == Magics.FILE_STR) {
                parseHandle.addHeader(request, "Content-Type", "multipart/form-data")
                parseHandle.addFormFileParam(request, parameter.name!!,
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, attrFromParam)
            } else {
                parseHandle.addParam(request, parameter.name!!, tinyQueryParam(typeObject?.toString()),
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, attrFromParam)
            }
        } catch (e: Exception) {
            logger!!.error("error to parse[" + paramType.canonicalText + "] as ModelAttribute")
            logger.traceError(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun addModelAttr(parameter: PsiParameter, request: Request, parseHandle: ParseHandle, attrFromParam: String? = null) {

        val paramType = parameter.type
        try {
            val unboxType = psiClassHelper!!.unboxArrayOrList(paramType)
            val typeObject = psiClassHelper.getTypeObject(unboxType, parameter, JsonOption.READ_COMMENT)
            if (typeObject != null && typeObject is KV<*, *>) {
                val fields = typeObject as KV<String, Any>
                val comment: KV<String, Any>? = fields.getAs(Attrs.COMMENT_ATTR)
                val required: KV<String, Any>? = fields.getAs(Attrs.REQUIRED_ATTR)
                parseHandle.addHeader(request, "Content-Type", "application/x-www-form-urlencoded")
                fields.forEachValid { filedName, fieldVal ->
                    val fv = deepComponent(fieldVal)
                    if (fv == Magics.FILE_STR) {
                        parseHandle.addHeader(request, "Content-Type", "multipart/form-data")
                        parseHandle.addFormFileParam(request, filedName,
                                required?.getAs(filedName) ?: false,
                                KVUtils.getUltimateComment(comment, filedName))
                    } else {
                        parseHandle.addFormParam(request, filedName, null,
                                required?.getAs(filedName) ?: false,
                                KVUtils.getUltimateComment(comment, filedName))
                    }
                }
            } else if (typeObject == Magics.FILE_STR) {
                parseHandle.addHeader(request, "Content-Type", "multipart/form-data")
                parseHandle.addFormFileParam(request, parameter.name!!,
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, attrFromParam)
            } else {
                parseHandle.addParam(request, parameter.name!!, tinyQueryParam(typeObject?.toString()),
                        ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) ?: false, attrFromParam)
            }
        } catch (e: Exception) {
            logger!!.error("error to parse[" + paramType.canonicalText + "] as ModelAttribute")
            logger.traceError(e)
        }
    }

    private fun parseRequestBody(psiType: PsiType?, context: PsiElement): Any? {
        return psiClassHelper!!.getTypeObject(psiType, context, JsonOption.READ_COMMENT)
    }

    private fun parseResponseBody(psiType: PsiType?, method: PsiMethod): Any? {

        if (psiType == null) {
            return null
        }

        return when {
            needInfer() && !duckTypeHelper!!.isQualified(psiType, method) -> {
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

    companion object {
        val SPRING_REQUEST_MAPPING_ANNOTATIONS: Set<String> = setOf(SpringClassName.REQUESTMAPPING_ANNOTATION,
                SpringClassName.GET_MAPPING,
                SpringClassName.DELETE_MAPPING,
                SpringClassName.PATCH_MAPPING,
                SpringClassName.POST_MAPPING,
                SpringClassName.PUT_MAPPING)

        const val REQUEST_HEADER = "org.springframework.web.bind.annotation.RequestHeader"

        const val REQUEST_HEADER_DEFAULT_NONE = "\n\t\t\n\t\t\n\uE000\uE001\uE002\n\t\t\t\t\n"

        const val ESCAPE_REQUEST_HEADER_DEFAULT_NONE = "\\n\\t\\t\\n\\t\\t\\n\\uE000\\uE001\\uE002\\n\\t\\t\\t\\t\\n"
    }
}