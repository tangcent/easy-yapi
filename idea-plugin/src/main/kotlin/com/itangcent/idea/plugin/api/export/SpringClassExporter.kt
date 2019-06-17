package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.containers.isNullOrEmpty
import com.itangcent.common.constant.Attrs
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.exception.ProcessCanceledException
import com.itangcent.common.exporter.*
import com.itangcent.common.model.Header
import com.itangcent.common.model.Request
import com.itangcent.common.model.RequestHandle
import com.itangcent.common.model.Response
import com.itangcent.idea.constant.SpringAttrs
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.api.MethodReturnInferHelper
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.utils.traceError
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.*
import com.itangcent.intellij.spring.MultipartFile
import com.itangcent.intellij.spring.SpringClassName
import com.itangcent.intellij.util.DocCommentUtils
import com.itangcent.intellij.util.KV
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
    private val commonRules: CommonRules? = null

    @Inject
    private val docParseHelper: DefaultDocParseHelper? = null

    @Inject
    private val settingBinder: SettingBinder? = null

    @Inject
    private val duckTypeHelper: DuckTypeHelper? = null

    @Inject
    private val methodReturnInferHelper: MethodReturnInferHelper? = null

    @Inject
    private val ruleParser: RuleParser? = null

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
                        exportMethodApi(method, basePath, ctrlHttpMethod, parseHandle, requestHandle)
                    }
                }
            }
        } finally {
            statusRecorder.endWork()
        }
    }

    private fun isCtrl(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any {
            SpringAttrs.SPRING_CONTROLLER_ANNOTATION.contains(it.qualifiedName)
        }
    }

    private fun shouldIgnore(psiClass: PsiClass): Boolean {
        val ignoreRules = commonRules!!.readIgnoreRules()
        val context = ruleParser!!.contextOf(psiClass)
        return ignoreRules.any { it.compute(context) == true }
    }

    private fun exportMethodApi(method: PsiMethod, basePath: String, ctrlHttpMethod: String
                                , parseHandle: ParseHandle, requestHandle: RequestHandle) {

        actionContext!!.checkStatus()
        val requestMappingAnn = findRequestMapping(method) ?: return
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

        val deprecateInfo = findDeprecatedOfMethod(method, parseHandle)
        if (deprecateInfo != null) {
            parseHandle.appendDesc(request, "[deprecate]$deprecateInfo")
        }

        parseHandle.setName(request, attr ?: method.name)

        parseHandle.setMethod(request, httpMethod)

        processMethodParameters(method, request, parseHandle)

        processResponse(method, request, parseHandle)

        requestHandle(request)
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

    private fun findRequestMapping(method: PsiMethod): PsiAnnotation? {
        return findRequestMappingInAnn(method)
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
        return DocCommentUtils.findDocsByTag(method.docComment, "deprecated")?.let { docParseHelper!!.resolveLinkInAttr(it, method, parseHandle) }
    }

    protected fun findDeprecatedOfClass(psiClass: PsiClass, parseHandle: ParseHandle): String? {
        return DocCommentUtils.findDocsByTag(psiClass.docComment, "deprecated")?.let { docParseHelper!!.resolveLinkInAttr(it, psiClass, parseHandle) }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun findAttrForParam(paramName: String?, docComment: KV<String, Any>?): String? {
        return when {
            paramName == null -> null
            docComment == null -> null
            docComment.containsKey("$paramName@options") -> {
                val options = docComment["$paramName@options"] as List<Map<String, Any?>>
                "${docComment[paramName]}${getOptionDesc(options)}"
            }
            else -> docComment[paramName] as String?
        }
    }

    /**
     * get description of options
     */
    private fun getOptionDesc(options: List<Map<String, Any?>>): String? {
        return options.stream()
                .map { it["value"].toString() + " :" + it["desc"] }
                .filter { it != null }
                .reduce { s1, s2 -> s1 + "\n" + s2 }
                .orElse(null)
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
        val ignoreRules = commonRules!!.readIgnoreRules()
        cls.allMethods
                .filter { !PsiClassHelper.JAVA_OBJECT_METHODS.contains(it.name) }
                .filter { !it.hasModifier(JvmModifier.STATIC) }
                .filter { ignoreRules.isEmpty() || !shouldIgnore(it) }
                .forEach(handle)
    }

    private fun shouldIgnore(psiMethod: PsiMethod): Boolean {
        val ignoreRules = commonRules!!.readIgnoreRules()
        val context = ruleParser!!.contextOf(psiMethod)
        return when {
            ignoreRules.any { it.compute(context) == true } -> {
                logger!!.info("ignore method:" + PsiClassUtils.fullNameOfMethod(psiMethod))
                true
            }
            else -> false
        }
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
                if (StringUtils.isBlank(paramName)) {
                    paramName = param.name!!
                }

                val paramType = param.type
                val unboxType = psiClassHelper!!.unboxArrayOrList(paramType)
                var defaultVal: Any? = null
                if (unboxType is PsiPrimitiveType) { //primitive Type
                    defaultVal = PsiTypesUtil.getDefaultValue(unboxType)
                } else if (psiClassHelper.isNormalType(unboxType.canonicalText)) {//normal type
                    defaultVal = psiClassHelper.getDefaultValue(unboxType.canonicalText)
                } else if (paramType.canonicalText.contains(SpringClassName.MULTIPARTFILE)) {
                    if (httpMethod == HttpMethod.GET) {
                        //can not upload file in a GET method
                        logger!!.error("Couldn't upload file in 'GET':[$httpMethod:${request.path}],param:${param.name} type:{${paramType.canonicalText}}")
                        continue
                    }

                    if (httpMethod == HttpMethod.NO_METHOD) {
                        httpMethod = HttpMethod.POST
                    }

                    parseHandle.addFormFileParam(request, paramName!!, required, findAttrForParam(param.name, paramDocComment))
                    continue
                } else if (SpringAttrs.SPRING_REQUEST_RESPONSE.contains(unboxType.presentableText)) {
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
                        addModelAttrAsQuery(param, request, parseHandle)
                    } else {
                        if (httpMethod == HttpMethod.NO_METHOD) {
                            httpMethod = HttpMethod.POST
                        }
                        addModelAttr(param, request, parseHandle)
                    }
                }
            }

        }
        if (httpMethod == HttpMethod.NO_METHOD) {
            httpMethod = HttpMethod.GET
        }
        parseHandle.setMethod(request, httpMethod)
    }

    fun addModelAttrAsQuery(parameter: PsiParameter, request: Request, parseHandle: ParseHandle) {
        val paramType = parameter.type
        try {
            val paramCls = PsiUtil.resolveClassInClassTypeOnly(psiClassHelper!!.unboxArrayOrList(paramType))
            val fields = psiClassHelper.getFields(paramCls, JsonOption.READ_COMMENT)
            val comment: KV<String, Any>? = fields.getAs(Attrs.COMMENT_ATTR)
            fields.forEach { filedName, fieldVal ->
                if (filedName != Attrs.COMMENT_ATTR) {
                    parseHandle.addParam(request, filedName, tinyQueryParam(fieldVal?.toString()), false, comment?.get("filedName") as String?)
                }
            }
        } catch (e: Exception) {
            logger!!.error("error to parse[" + paramType.canonicalText + "] as ModelAttribute")
        }
    }

    fun addModelAttr(parameter: PsiParameter, request: Request, parseHandle: ParseHandle) {

        val paramType = parameter.type
        try {
            val paramCls = PsiUtil.resolveClassInClassTypeOnly(psiClassHelper!!.unboxArrayOrList(paramType))
            val fields = psiClassHelper.getFields(paramCls, JsonOption.READ_COMMENT)
            val comment: KV<String, Any>? = fields.getAs(Attrs.COMMENT_ATTR)
            parseHandle.addHeader(request, "Content-Type", "application/x-www-form-urlencoded")
            fields.forEach { filedName, fieldVal ->
                if (filedName != Attrs.COMMENT_ATTR) {
                    val fv = deepComponent(fieldVal)
                    if (fv is MultipartFile) {
                        parseHandle.addHeader(request, "Content-Type", "multipart/form-data")
                        parseHandle.addFormFileParam(request, filedName, false, comment?.getAs(filedName))
                    } else {
                        parseHandle.addFormParam(request, filedName, null, comment?.getAs(filedName))
                    }
                }
            }
        } catch (e: Exception) {
            logger!!.error("error to parse[" + paramType.canonicalText + "] as ModelAttribute")
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

        val REQUEST_HEADER = "org.springframework.web.bind.annotation.RequestHeader"

        val REQUEST_HEADER_DEFAULT_NONE = "\n\t\t\n\t\t\n\uE000\uE001\uE002\n\t\t\t\t\n"

        val ESCAPE_REQUEST_HEADER_DEFAULT_NONE = "\\n\\t\\t\\n\\t\\t\\n\\uE000\\uE001\\uE002\\n\\t\\t\\t\\t\\n"
    }
}