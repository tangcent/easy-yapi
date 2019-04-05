package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.containers.isNullOrEmpty
import com.itangcent.common.constant.Attrs
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.exporter.*
import com.itangcent.common.model.Request
import com.itangcent.common.model.RequestHandle
import com.itangcent.common.model.Response
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.constant.SpringAttrs
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.JsonOption
import com.itangcent.intellij.psi.PsiAnnotationUtils
import com.itangcent.intellij.psi.PsiClassHelper
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.spring.MultipartFile
import com.itangcent.intellij.spring.SpringClassName
import com.itangcent.intellij.util.DocCommentUtils
import com.itangcent.intellij.util.KV
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import java.util.*
import java.util.regex.Pattern

open class SpringClassApiExporter : ClassExporter<PsiClass> {

    @Inject
    protected val logger: Logger? = null

    @Inject
    protected val psiClassHelper: PsiClassHelper? = null

    @Inject
    protected val commonRules: CommonRules? = null

    override fun export(cls: PsiClass, parseHandle: ParseHandle, requestHandle: RequestHandle) {
        if (!isCtrl(cls)) {
            return
        }

        if (shouldIgnore(cls)) {
            logger!!.info("ignore class:" + cls.qualifiedName)
            return
        }

        val ctrlRequestMappingAnn = findRequestMapping(cls)
        val basePath: String = findHttpPath(ctrlRequestMappingAnn) ?: ""

        val ctrlHttpMethod = findHttpMethod(ctrlRequestMappingAnn)

        foreachMethod(cls) { method ->
            exportMethodApi(method, basePath, ctrlHttpMethod, parseHandle, requestHandle)
        }
    }

    protected fun isCtrl(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any {
            SpringAttrs.SPRING_CONTROLLER_ANNOTATION.contains(it.qualifiedName)
        }
    }

    protected fun shouldIgnore(psiClass: PsiClass): Boolean {
        val ignoreRules = commonRules!!.readIgnoreRules()
        return ignoreRules.any { it(psiClass, psiClass, psiClass) }
    }

    private fun exportMethodApi(method: PsiMethod, basePath: String, ctrlHttpMethod: String
                                , parseHandle: ParseHandle, requestHandle: RequestHandle) {

        val requestMappingAnn = findRequestMapping(method) ?: return
        val request = Request()

        var httpMethod = findHttpMethod(requestMappingAnn)
        if (httpMethod == HttpMethod.NO_METHOD && ctrlHttpMethod != HttpMethod.NO_METHOD) {
            httpMethod = ctrlHttpMethod
        }

        val httpPath = contractPath(basePath, findHttpPath(requestMappingAnn))!!
        parseHandle.setPath(request, httpPath)

        var attr: String? = null
        val attrOfMethod = findAttrOfMethod(method, parseHandle)!!
        if (attrOfMethod.contains("\n")) {//multi line
            val lines = attrOfMethod.lines()
            for (line in lines) {
                if (line.isNotBlank()) {
                    attr = line
                    break
                }
            }
        } else {
            attr = attrOfMethod
        }

        parseHandle.appendDesc(request, attrOfMethod)

        parseHandle.setName(request, attr ?: method.name)

        parseHandle.setMethod(request, httpMethod)

        processMethodParameters(method, request, parseHandle)

        processResponse(method, request, parseHandle)

        requestHandle(request)
    }

    protected fun processResponse(method: PsiMethod, request: Request, parseHandle: ParseHandle) {

        val returnType = method.returnType
        if (returnType != null) {
            try {
                val response = Response()
                parseHandle.setResponseCode(response, 200)
                val typedResponse = psiClassHelper!!.getTypeObject(returnType, method)

                parseHandle.setResponseBody(response, "raw", GsonUtils.prettyJson(typedResponse))

                parseHandle.addResponseHeader(response, "content-type", "application/json;charset=UTF-8")

                parseHandle.addResponse(request, response)

            } catch (e: Throwable) {
                logger!!.error("error to parse body:" + ExceptionUtils.getStackTrace(e))
            }
        }
    }

    /**
     * queryParam中的数组元素需要拆开
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

    protected fun findHttpPath(requestMappingAnn: PsiAnnotation?): String? {
        val path = PsiAnnotationUtils.findAttr(requestMappingAnn, "path", "value") ?: return null

        return when {
            path.contains(",") -> PsiAnnotationUtils.tinyAnnStr(path.substringBefore(','))
            else -> path
        }
    }

    protected fun findHttpMethod(requestMappingAnn: PsiAnnotation?): String {
        if (requestMappingAnn != null) {
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
                else -> method
            }
        }
        return HttpMethod.NO_METHOD
    }

    protected fun findRequestMapping(psiClass: PsiClass): PsiAnnotation? {
        val requestMappingAnn = PsiAnnotationUtils.findAnn(psiClass, SpringClassName.REQUESTMAPPING_ANNOTATION)
        if (requestMappingAnn != null) return requestMappingAnn
        var superCls = psiClass.superClass
        while (superCls != null) {
            val requestMappingAnnInSuper = PsiAnnotationUtils.findAnn(superCls, SpringClassName.REQUESTMAPPING_ANNOTATION)
            if (requestMappingAnnInSuper != null) return requestMappingAnnInSuper
            superCls = superCls.superClass
        }
        return null
    }

    protected fun findRequestMapping(method: PsiMethod): PsiAnnotation? {
        return PsiAnnotationUtils.findAnn(method, SpringClassName.REQUESTMAPPING_ANNOTATION)
    }

    protected fun findRequestBody(parameter: PsiParameter): PsiAnnotation? {
        return PsiAnnotationUtils.findAnn(parameter, SpringClassName.REQUESTBOODY_ANNOTATION)
    }

    protected fun findModelAttr(parameter: PsiParameter): PsiAnnotation? {
        return PsiAnnotationUtils.findAnn(parameter, SpringClassName.MODELATTRIBUTE_ANNOTATION)
    }

    protected fun findPathVariable(parameter: PsiParameter): PsiAnnotation? {
        return PsiAnnotationUtils.findAnn(parameter, SpringClassName.PATHVARIABLE_ANNOTATION)
    }

    protected fun findRequestParam(parameter: PsiParameter): PsiAnnotation? {
        return PsiAnnotationUtils.findAnn(parameter, SpringClassName.REQUESTPARAM_ANNOTATION)
    }

    protected fun findParamName(requestParamAnn: PsiAnnotation?): String? {
        return PsiAnnotationUtils.findAttr(requestParamAnn, "name", "value")
    }

    protected fun findParamRequired(requestParamAnn: PsiAnnotation?): Boolean? {
        val required = PsiAnnotationUtils.findAttr(requestParamAnn, "name", "required") ?: return null
        return when {
            required.contains("false") -> false
            else -> null
        }
    }

    protected fun findAttrOfMethod(method: PsiMethod, parseHandle: ParseHandle): String? {
        val docComment = method.docComment

        val docText = DocCommentUtils.getAttrOfDocComment(docComment)
        return when {
            StringUtils.isBlank(docText) -> method.name
            else -> resolveLinkInAttr(docText, method, parseHandle)
        }
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
     * 获得枚举值备注信息
     */
    protected fun getOptionDesc(options: List<Map<String, Any?>>): String? {
        return options.stream()
                .map { it["value"].toString() + " :" + it["desc"] }
                .filter { it != null }
                .reduce { s1, s2 -> s1 + "\n" + s2 }
                .orElse(null)
    }

    protected fun extractParamComment(psiMethod: PsiMethod): KV<String, Any>? {
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
                }
                methodParamComment?.set(name!!, value!!)
            }
        }
        return methodParamComment
    }

    protected fun foreachMethod(cls: PsiClass, handle: (PsiMethod) -> Unit) {
        val ignoreRules = commonRules!!.readIgnoreRules()
        cls.allMethods
                .filter { !PsiClassHelper.JAVA_OBJECT_METHODS.contains(it.name) }
                .filter { !it.hasModifier(JvmModifier.STATIC) }
                .filter { ignoreRules.isEmpty() || !shouldIgnore(it) }
                .forEach(handle)
    }

    protected fun shouldIgnore(psiMethod: PsiMethod): Boolean {
        val ignoreRules = commonRules!!.readIgnoreRules()
        return when {
            ignoreRules.any { it(psiMethod, psiMethod, psiMethod) } -> {
                logger!!.info("ignore method:" + PsiClassUtils.fullNameOfMethod(psiMethod))
                true
            }
            else -> false
        }
    }

    protected fun processMethodParameters(method: PsiMethod, request: Request,
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

                val pathVariableAnn = findPathVariable(param)
                if (pathVariableAnn != null) {
                    val attr = findAttrForParam(param.name, paramDocComment)
                    if (attr.isNullOrBlank()) continue

                    var pathName = PsiAnnotationUtils.findAttr(pathVariableAnn,
                            "value")
                    if (pathName == null) {
                        pathName = param.name
                    }

                    parseHandle.addPathParam(request, pathName!!, attr)
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
            fields.forEach { filedName, fieldVal ->
                if (filedName != Attrs.COMMENT_ATTR) {
                    val fv = deepComponent(fieldVal)
                    if (fv is MultipartFile) {
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

    private fun resolveLinkInAttr(attr: String?, psiMember: PsiMember, parseHandle: ParseHandle): String? {
        if (attr.isNullOrBlank()) return attr

        if (attr.contains("@link")) {
            val pattern = Pattern.compile("\\{@link (.*?)\\}")
            val matcher = pattern.matcher(attr)

            val sb = StringBuffer()
            while (matcher.find()) {
                matcher.appendReplacement(sb, "")
                val linkClassAndMethod = matcher.group(1)
                val linkClassName = linkClassAndMethod.substringBefore("#")
                val linkMethodOrProperty = linkClassAndMethod.substringAfter("#", "").trim()
                val linkClass = psiClassHelper!!.resolveClass(linkClassName, psiMember) ?: continue
                if (linkMethodOrProperty.isBlank()) {
                    sb.append(parseHandle.linkToClass(linkClass))
                } else {
                    val methodOrProperty = psiClassHelper.resolvePropertyOrMethodOfClass(linkClass, linkMethodOrProperty)
                            ?: continue
                    when (methodOrProperty) {
                        is PsiMethod -> sb.append(parseHandle.linkToMethod(methodOrProperty))
                        is PsiField -> sb.append(parseHandle.linkToProperty(methodOrProperty))
                        else -> sb.append("[$linkClassAndMethod]")
                    }
                }
            }
            matcher.appendTail(sb)
            return sb.toString()
        }

        return attr
    }

    protected open fun parseRequestBody(psiType: PsiType?, context: PsiElement): Any? {
        return psiClassHelper!!.getTypeObject(psiType, context, JsonOption.READ_COMMENT)
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
}