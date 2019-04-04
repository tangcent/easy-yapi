package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.util.containers.isNullOrEmpty
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.JsonOption
import com.itangcent.intellij.psi.PsiAnnotationUtils
import com.itangcent.intellij.psi.PsiClassHelper
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.spring.SpringClassName
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.DocCommentUtils
import com.itangcent.intellij.util.KV
import org.apache.commons.lang3.StringUtils
import java.util.*
import java.util.function.Consumer
import java.util.regex.Pattern

open class ApiExporter {
    @Inject
    protected val logger: Logger? = null

    @Inject
    protected val psiClassHelper: PsiClassHelper? = null

    @Inject
    protected val commonRules: CommonRules? = null

    protected fun isJavaFile(psiFile: PsiFile): Boolean {
        if (psiFile !is PsiJavaFile) return false
        if (!(psiFile.name.endsWith(".java"))) return false
        return true
    }

    protected fun isCtrl(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { it.qualifiedName?.contains("Controller") ?: false }
    }

    protected fun shouldIgnore(psiClass: PsiClass): Boolean {
        val ignoreRules = commonRules!!.readIgnoreRules()
        return when {
            ignoreRules.any { it(psiClass, psiClass, psiClass) } -> {
                logger!!.info("ignore class:" + psiClass.qualifiedName)
                true
            }
            else -> false
        }
    }

    protected fun findModule(psiDirectory: PsiDirectory): String? {
        val currentPath = ActionUtils.findCurrentPath(psiDirectory)
        return findModuleByPath(currentPath)
    }

    protected fun findModule(psiFile: PsiFile): String? {
        val currentPath = ActionUtils.findCurrentPath(psiFile)
        return findModuleByPath(currentPath)
    }

    private fun findModuleByPath(path: String?): String? {
        if (path == null) return null
        var module: String? = null
        try {
            var currentPath = path
            when {
                currentPath.contains("/src/") -> currentPath = StringUtils.substringBefore(currentPath, "/src/")
                currentPath.contains("/main/") -> currentPath = StringUtils.substringBefore(currentPath, "/main/")
                currentPath.contains("/java/") -> currentPath = StringUtils.substringBefore(currentPath, "/java/")
            }
            module = StringUtils.substringAfterLast(currentPath, "/")
        } catch (e: Exception) {
            logger!!.error("error in findCurrentPath:" + e.toString())
        }
        return module

    }

    protected fun traversal(psiDirectory: PsiDirectory,
                            fileFilter: (PsiFile) -> Boolean,
                            fileHandle: Consumer<PsiFile>) {

        val dirStack: Stack<PsiDirectory> = Stack()
        var dir: PsiDirectory? = psiDirectory
        while (dir != null) {
            dir.files.filter { fileFilter(it) }
                    .forEach { fileHandle.accept(it) }

            for (subdirectory in dir.subdirectories) {
                dirStack.push(subdirectory)
            }
            if (dirStack.isEmpty()) break
            dir = dirStack.pop()
        }
    }

    protected fun findModule(): String? {
        val currentPath = ActionUtils.findCurrentPath()
        return findModuleByPath(currentPath)
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
            var method = PsiAnnotationUtils.findAttr(requestMappingAnn, "method") ?: return NO_METHOD
            if (method.contains(",")) {
                method = method.substringBefore(",")
            }
            return when {
                StringUtils.isBlank(method) -> {
                    NO_METHOD
                }
                method.startsWith("RequestMethod.") -> {
                    method.removePrefix("RequestMethod.")
                }
                else -> method
            }
        }
        return NO_METHOD
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

    protected fun findAttrOfClass(cls: PsiClass): String? {
        val docComment = cls.docComment
        val docText = DocCommentUtils.getAttrOfDocComment(docComment)
        return when {
            StringUtils.isBlank(docText) -> cls.name
            else -> resolveLinkInAttr(docText, cls)
        }
    }

    protected fun findAttrOfMethod(method: PsiMethod): String? {
        val docComment = method.docComment

        val docText = DocCommentUtils.getAttrOfDocComment(docComment)
        return when {
            StringUtils.isBlank(docText) -> method.name
            else -> resolveLinkInAttr(docText, method)
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

    protected fun findModuleOfClass(cls: PsiClass): String? {
        val moduleRules = commonRules!!.readModuleRules()
        if (moduleRules.isEmpty()) return null

        return moduleRules
                .map { it(cls, cls, cls) }
                .firstOrNull { it != null }
    }

    protected fun findReturnOfMethod(method: PsiMethod): String? {
        return DocCommentUtils.findDocsByTag(method.docComment, "return")
    }

    protected fun findDeprecatedOfMethod(method: PsiMethod): String? {
        return DocCommentUtils.findDocsByTag(method.docComment, "deprecated")?.let { resolveLinkInAttr(it, method) }
    }

    protected fun findDeprecatedOfClass(psiClass: PsiClass): String? {
        return DocCommentUtils.findDocsByTag(psiClass.docComment, "deprecated")?.let { resolveLinkInAttr(it, psiClass) }
    }

    protected fun processMethodParameters(method: PsiMethod, itemInfo: ItemInfo) {

        val params = method.parameterList.parameters
        var httpMethod = itemInfo.getHttpMethod()
        if (params.isNotEmpty()) {

            val paramDocComment = extractParamComment(method)

            for (param in params) {

                val requestBodyAnn = findRequestBody(param)
                if (requestBodyAnn != null) {
                    if (httpMethod == ApiExporter.NO_METHOD) {
                        httpMethod = "POST"
                    }
                    itemInfo.setHeader("Content-Type", "application/json")
                    itemInfo.setJsonBody(
                            parseRequestBody(param.type, method),
                            findAttrForParam(param.name, paramDocComment)
                    )
                    continue
                }

                val modelAttrAnn = findModelAttr(param)
                if (modelAttrAnn != null) {
                    if (httpMethod == "GET") {
                        itemInfo.addModelAttrAsQuery(param)
                    } else {
                        if (httpMethod == ApiExporter.NO_METHOD) {
                            httpMethod = "POST"
                        }
                        itemInfo.addModelAttr(param)
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

                    pathName?.let { itemInfo.addPathVal(it, attr) }
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
                    defaultVal = PsiClassHelper.multipartFileInstance
                    if (httpMethod == "GET") {
                        //can not upload file in a GET method
                        logger!!.error("Couldn't upload file in 'GET':[$httpMethod:${itemInfo.getHttpPath()}],param:${param.name} type:{${paramType.canonicalText}}")
                        continue
                    }

                    if (httpMethod == ApiExporter.NO_METHOD) {
                        httpMethod = "POST"
                    }

                    itemInfo.addModelAttr(paramName!!, defaultVal, findAttrForParam(param.name, paramDocComment))
                    continue
                } else if (SPRING_REQUEST_RESPONSE.contains(unboxType.presentableText)) {
                    continue
                }

                if (defaultVal != null) {
                    itemInfo.addQuery2Url(paramName!!
                            , defaultVal
                            , required
                            , findAttrForParam(param.name, paramDocComment))
                } else {
                    if (httpMethod == "GET") {
                        itemInfo.addModelAttrAsQuery(param)
                    } else {
                        if (httpMethod == ApiExporter.NO_METHOD) {
                            httpMethod = "POST"
                        }
                        itemInfo.addModelAttr(param)
                    }
                }
            }

        }
        if (httpMethod == ApiExporter.NO_METHOD) {
            httpMethod = "GET"
        }
        itemInfo.setHttpMethod(httpMethod)
    }

    protected fun deepComponent(obj: Any?): Any? {
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

    protected fun resolveLinkInAttr(attr: String?, psiMember: PsiMember): String? {
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
                    sb.append(linkToClass(linkClass))
                } else {
                    val methodOrProperty = psiClassHelper.resolvePropertyOrMethodOfClass(linkClass, linkMethodOrProperty)
                            ?: continue
                    when (methodOrProperty) {
                        is PsiMethod -> sb.append(linkToMethod(methodOrProperty))
                        is PsiField -> sb.append(linkToProperty(methodOrProperty))
                        else -> sb.append("[$linkClassAndMethod]")
                    }
                }
            }
            matcher.appendTail(sb)
            return sb.toString()
        }

        return attr
    }

    open protected fun linkToClass(linkClass: PsiClass): String? {
        val attrOfClass = DocCommentUtils.getAttrOfDocComment(linkClass.docComment)
        return when {
            attrOfClass.isNullOrBlank() -> "[${linkClass.name}]"
            else -> "[$attrOfClass]"
        }
    }

    open protected fun linkToMethod(linkMethod: PsiMethod): String? {
        val attrOfMethod = DocCommentUtils.getAttrOfDocComment(linkMethod.docComment)
        return when {
            attrOfMethod.isNullOrBlank() -> "[${PsiClassUtils.fullNameOfMethod(linkMethod)}]"
            else -> "[$attrOfMethod]"
        }
    }

    open protected fun linkToProperty(linkField: PsiField): String? {
        val attrOfProperty = DocCommentUtils.getAttrOfDocComment(linkField.docComment)
        return when {
            attrOfProperty.isNullOrBlank() -> "[${PsiClassUtils.fullNameOfField(linkField)}]"
            else -> "[$attrOfProperty]"
        }
    }

    open protected fun parseRequestBody(psiType: PsiType?, context: PsiElement): Any? {
        return psiClassHelper!!.getTypeObject(psiType, context, JsonOption.READ_COMMENT)
    }

    protected interface ItemInfo {

        fun setHttpMethod(httpMethod: String)

        fun getHttpMethod(): String

        fun getHttpPath(): String

        fun addModelAttr(parameter: PsiParameter)

        fun addModelAttrAsQuery(parameter: PsiParameter)

        fun addModelAttr(name: String, value: Any?, attr: String?)

        fun addQuery2Url(paramName: String, defaultVal: Any?, attr: String?) {
            addQuery2Url(paramName, defaultVal, false, attr)
        }

        fun addQuery2Url(paramName: String, defaultVal: Any?, required: Boolean, attr: String?)

        fun addPathVal(name: String, desc: String)

        fun setJsonBody(body: Any?, desc: String?)

        fun appendDesc(desc: String?)

        fun setHeader(name: String, value: String)

        fun getData(): Any
    }

    companion object {
        val NO_METHOD = "ALL"
        val COMMENT_ATTR = "@comment"
        val SPRING_REQUEST_RESPONSE: Array<String> = arrayOf("HttpServletRequest", "HttpServletResponse")

        val EMPTY_ARR: List<String> = Collections.emptyList<String>()!!
        val EMPTY_PARAMS: List<String> = Collections.emptyList<String>()
        val EMPTY_TAGS: List<String> = Collections.emptyList<String>()
    }
}