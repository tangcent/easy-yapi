package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.model.Header
import com.itangcent.common.model.Request
import com.itangcent.common.utils.KV
import com.itangcent.common.utils.any
import com.itangcent.common.utils.isNullOrEmpty
import com.itangcent.common.utils.tinyString
import com.itangcent.idea.plugin.utils.SpringClassName
import com.itangcent.intellij.jvm.AnnotationHelper
import com.itangcent.intellij.psi.ClassRuleKeys
import org.apache.commons.lang3.StringUtils

open class SpringRequestClassExporter : AbstractRequestClassExporter() {

    @Inject
    private val annotationHelper: AnnotationHelper? = null

    @Inject
    private val commentResolver: CommentResolver? = null

    override fun processClass(cls: PsiClass, kv: KV<String, Any?>) {

        val ctrlRequestMappingAnn = findRequestMapping(cls)
        var basePath: String = findHttpPath(ctrlRequestMappingAnn) ?: ""
        val prefixPath = ruleComputer!!.computer(ClassExportRuleKeys.CLASS_PREFIX_PATH, cls)
        if (!prefixPath.isNullOrBlank()) {
            basePath = contractPath(prefixPath, basePath) ?: ""
        }

        val ctrlHttpMethod = findHttpMethod(ctrlRequestMappingAnn)

        kv["basePath"] = basePath
        kv["ctrlHttpMethod"] = ctrlHttpMethod
    }

    override fun hasApi(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any {
            SpringClassName.SPRING_CONTROLLER_ANNOTATION.contains(it.qualifiedName)
        }
    }

    override fun isApi(psiMethod: PsiMethod): Boolean {
        return findRequestMappingInAnn(psiMethod) != null
    }

    override fun processMethodParameter(method: PsiMethod, request: Request, param: PsiParameter, paramDesc: String?) {

        if (isRequestBody(param)) {
            if (request.method == HttpMethod.NO_METHOD) {
                requestHelper!!.setMethod(request, HttpMethod.POST)
            }
            requestHelper!!.addHeader(request, "Content-Type", "application/json")
            requestHelper.setJsonBody(
                    request,
                    parseRequestBody(param.type, method),
                    paramDesc
            )
            return
        }

        if (isModelAttr(param)) {
            if (request.method == HttpMethod.GET) {
                addParamAsQuery(param, request)
            } else {
                if (request.method == HttpMethod.NO_METHOD) {
                    requestHelper!!.setMethod(request, HttpMethod.POST)
                }

                addParamAsForm(param, request)
            }
            return
        }

        var ultimateComment = (paramDesc ?: "")
        commentResolver!!.resolveCommentForType(param.type, param)?.let {
            ultimateComment = "$ultimateComment $it"
        }
        val requestHeaderAnn = findRequestHeader(param)
        if (requestHeaderAnn != null) {

            var headName = requestHeaderAnn.any("value", "name")
            if (headName.isNullOrEmpty()) {
                headName = param.name
            }

            var required = findParamRequired(requestHeaderAnn)
            if (!required && ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, param) == true) {
                required = true
            }

            var defaultValue = requestHeaderAnn["defaultValue"]

            if (defaultValue == null
                    || defaultValue == SpringClassName.ESCAPE_REQUEST_HEADER_DEFAULT_NONE
                    || defaultValue == SpringClassName.REQUEST_HEADER_DEFAULT_NONE) {
                defaultValue = ""
            }

            val header = Header()
            header.name = headName?.toString()
            header.value = defaultValue.toString()
            header.example = defaultValue.toString()
            header.desc = ultimateComment
            header.required = required
            requestHelper!!.addHeader(request, header)
            return
        }

        val pathVariableAnn = findPathVariable(param)
        if (pathVariableAnn != null) {

            var pathName = pathVariableAnn["value"]?.toString()

            if (pathName == null) {
                pathName = param.name
            }

            requestHelper!!.addPathParam(request, pathName!!, ultimateComment)
            return
        }

        var paramName: String? = null
        var required = false
        var defaultVal: Any? = null

        val requestParamAnn = findRequestParam(param)

        if (requestParamAnn != null) {
            paramName = findParamName(requestParamAnn)
            required = findParamRequired(requestParamAnn)

            defaultVal = requestParamAnn["defaultValue"]

            if (defaultVal == null
                    || defaultVal == SpringClassName.ESCAPE_REQUEST_HEADER_DEFAULT_NONE
                    || defaultVal == SpringClassName.REQUEST_HEADER_DEFAULT_NONE) {
                defaultVal = ""
            }
        }

        val readParamDefaultValue = readParamDefaultValue(param)

        if (!readParamDefaultValue.isNullOrBlank()) {
            defaultVal = readParamDefaultValue;
        }

        if (!required && ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, param) == true) {
            required = true
        }

        if (StringUtils.isBlank(paramName)) {
            paramName = param.name!!
        }

        val paramType = param.type
        val unboxType = psiClassHelper!!.unboxArrayOrList(paramType)
        val paramCls = PsiTypesUtil.getPsiClass(unboxType)
        if (unboxType is PsiPrimitiveType) { //primitive Type
            if (defaultVal == null || defaultVal == "") {
                defaultVal = PsiTypesUtil.getDefaultValue(unboxType)
                //Primitive type parameter is required
                //Optional primitive type parameter is present but cannot be translated into a null value due to being declared as a primitive type.
                //Consider declaring it as object wrapper for the corresponding primitive type.
                required = true
            }
        } else if (psiClassHelper.isNormalType(unboxType)) {//normal type
            if (defaultVal == null || defaultVal == "") {
                defaultVal = psiClassHelper.getDefaultValue(unboxType)
            }
        } else if (paramCls != null && ruleComputer!!.computer(ClassRuleKeys.TYPE_IS_FILE, paramCls) == true) {
            if (request.method == HttpMethod.GET) {
                //can not upload file in a GET method
                logger!!.error("Couldn't upload file in 'GET':[$request.method:${request.path}],param:${param.name} type:{${paramType.canonicalText}}")
                return
            }

            if (request.method == HttpMethod.NO_METHOD) {
                request.method = HttpMethod.POST
            }

            requestHelper!!.addHeader(request, "Content-Type", "multipart/form-data")
            requestHelper.addFormFileParam(request, paramName!!, required, ultimateComment)
            return
        } else if (SpringClassName.SPRING_REQUEST_RESPONSE.contains(unboxType.presentableText)) {
            //ignore @HttpServletRequest and @HttpServletResponse
            return
        }

        if (defaultVal != null) {
            requestHelper!!.addParam(request,
                    paramName!!
                    , defaultVal.toString()
                    , required
                    , ultimateComment)
        } else {
            if (request.method == HttpMethod.GET) {
                addParamAsQuery(param, request, ultimateComment)
            } else {
                if (request.method == HttpMethod.NO_METHOD) {
                    request.method = HttpMethod.POST
                }
                addParamAsForm(param, request, ultimateComment)
            }
        }

    }

    override fun processMethod(method: PsiMethod, kv: KV<String, Any?>, request: Request) {
        super.processMethod(method, kv, request)

        val basePath: String? = kv.getAs("basePath")
        val ctrlHttpMethod: String? = kv.getAs("ctrlHttpMethod")
        val requestMapping = findRequestMappingInAnn(method)
        var httpMethod = findHttpMethod(requestMapping)
        if (httpMethod == HttpMethod.NO_METHOD && ctrlHttpMethod != HttpMethod.NO_METHOD) {
            httpMethod = ctrlHttpMethod!!
        }
        request.method = httpMethod

        val httpPath = contractPath(basePath, findHttpPath(requestMapping))!!
        requestHelper!!.setPath(request, httpPath)
    }

    //region process spring annotation-------------------------------------------------------------------

    private fun findHttpPath(requestMappingAnn: Pair<Map<String, Any?>, String>?): String? {
        val path = requestMappingAnn?.first.any("path", "value")?.tinyString() ?: return null

        return when {
            path.contains(",") -> path.substringBefore(',')
            else -> path
        }
    }

    private fun findHttpMethod(requestMappingAnn: Pair<Map<String, Any?>, String>?): String {
        if (requestMappingAnn != null) {
            when {
                requestMappingAnn.second == SpringClassName.REQUESTMAPPING_ANNOTATION -> {
                    var method = requestMappingAnn.first["method"].tinyString() ?: return HttpMethod.NO_METHOD
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
                .map { annotationHelper!!.findAnnMap(ele, it)?.to(it) }
                .firstOrNull { it != null }
    }

    private fun isRequestBody(parameter: PsiParameter): Boolean {
        return annotationHelper!!.hasAnn(parameter, SpringClassName.REQUESTBOODY_ANNOTATION)
    }

    private fun isModelAttr(parameter: PsiParameter): Boolean {
        return annotationHelper!!.hasAnn(parameter, SpringClassName.MODELATTRIBUTE_ANNOTATION)
    }

    private fun findRequestHeader(parameter: PsiParameter): Map<String, Any?>? {
        return annotationHelper!!.findAnnMap(parameter, SpringClassName.REQUEST_HEADER)
    }

    private fun findPathVariable(parameter: PsiParameter): Map<String, Any?>? {
        return annotationHelper!!.findAnnMap(parameter, SpringClassName.PATHVARIABLE_ANNOTATION)
    }

    private fun findRequestParam(parameter: PsiParameter): Map<String, Any?>? {
        return annotationHelper!!.findAnnMap(parameter, SpringClassName.REQUESTPARAM_ANNOTATION)
    }

    private fun findParamName(requestParamAnn: Map<String, Any?>?): String? {
        return requestParamAnn.any("name", "value")?.toString()
    }

    private fun findParamRequired(requestParamAnn: Map<String, Any?>): Boolean {
        val required = requestParamAnn["required"]?.toString()
        return when {
            required?.contains("false") == true -> false
            else -> true
        }
    }

    //endregion process spring annotation-------------------------------------------------------------------

}
