package com.itangcent.idea.plugin.api.export

import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.model.Header
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.utils.SpringClassName
import com.itangcent.intellij.psi.ClassRuleKeys
import com.itangcent.intellij.psi.PsiAnnotationUtils
import com.itangcent.intellij.util.KV
import org.apache.commons.lang3.StringUtils

open class SpringRequestClassExporter : AbstractRequestClassExporter() {

    override fun processClass(cls: PsiClass, kv: KV<String, Any?>) {

        val ctrlRequestMappingAnn = findRequestMapping(cls)
        val basePath: String = findHttpPath(ctrlRequestMappingAnn) ?: ""

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


        val requestBodyAnn = findRequestBody(param)
        if (requestBodyAnn != null) {
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

        val modelAttrAnn = findModelAttr(param)
        if (modelAttrAnn != null) {
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

        val requestHeaderAnn = findRequestHeader(param)
        if (requestHeaderAnn != null) {

            var headName = PsiAnnotationUtils.findAttr(requestHeaderAnn,
                    "value")
            if (headName.isNullOrBlank()) {
                headName = PsiAnnotationUtils.findAttr(requestHeaderAnn,
                        "name")
            }
            if (headName.isNullOrBlank()) {
                headName = param.name
            }

            var required = findParamRequired(requestHeaderAnn) ?: true
            if (!required && ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, param) == true) {
                required = true
            }

            var defaultValue = PsiAnnotationUtils.findAttr(requestHeaderAnn,
                    "defaultValue")

            if (defaultValue == null
                    || defaultValue == SpringClassName.ESCAPE_REQUEST_HEADER_DEFAULT_NONE
                    || defaultValue == SpringClassName.REQUEST_HEADER_DEFAULT_NONE) {
                defaultValue = ""
            }

            val header = Header()
            header.name = headName
            header.value = defaultValue
            header.example = defaultValue
            header.desc = paramDesc
            header.required = required
            requestHelper!!.addHeader(request, header)
            return
        }

        val pathVariableAnn = findPathVariable(param)
        if (pathVariableAnn != null) {

            var pathName = PsiAnnotationUtils.findAttr(pathVariableAnn,
                    "value")
            if (pathName == null) {
                pathName = param.name
            }

            requestHelper!!.addPathParam(request, pathName!!, paramDesc ?: "")
            return
        }

        var paramName: String? = null
        var required = false
        var defaultVal: Any? = null

        val requestParamAnn = findRequestParam(param)

        if (requestParamAnn != null) {
            paramName = findParamName(requestParamAnn)
            required = findParamRequired(requestParamAnn) ?: true

            defaultVal = PsiAnnotationUtils.findAttr(requestParamAnn,
                    "defaultValue")

            if (defaultVal == null
                    || defaultVal == SpringClassName.ESCAPE_REQUEST_HEADER_DEFAULT_NONE
                    || defaultVal == SpringClassName.REQUEST_HEADER_DEFAULT_NONE) {
                defaultVal = ""
            }
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
        } else if (psiClassHelper.isNormalType(unboxType.canonicalText)) {//normal type
            if (defaultVal == null || defaultVal == "") {
                defaultVal = psiClassHelper.getDefaultValue(unboxType.canonicalText)
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
            requestHelper.addFormFileParam(request, paramName!!, required, paramDesc)
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
                    , paramDesc)
        } else {
            if (request.method == HttpMethod.GET) {
                addParamAsQuery(param, request, paramDesc)
            } else {
                if (request.method == HttpMethod.NO_METHOD) {
                    request.method = HttpMethod.POST
                }
                addParamAsForm(param, request, paramDesc)
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
        return SpringClassName.SPRING_REQUEST_MAPPING_ANNOTATIONS
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
        return PsiAnnotationUtils.findAnn(parameter, SpringClassName.REQUEST_HEADER)
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
        val required = PsiAnnotationUtils.findAttr(requestParamAnn, "required") ?: return null
        return when {
            required.contains("false") -> false
            else -> null
        }
    }

    //endregion process spring annotation-------------------------------------------------------------------

}