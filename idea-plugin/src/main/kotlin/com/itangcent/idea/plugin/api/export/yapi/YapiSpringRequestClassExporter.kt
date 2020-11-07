package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.itangcent.common.constant.Attrs
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.utils.asKV
import com.itangcent.common.utils.getAs
import com.itangcent.common.utils.getAsKv
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.*
import com.itangcent.common.utils.*
import com.itangcent.idea.plugin.api.export.*
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.jvm.element.ExplicitMethod
import com.itangcent.intellij.util.*
import org.apache.commons.lang3.StringUtils

open class YapiSpringRequestClassExporter : SpringRequestClassExporter() {

    @Inject
    private val configReader: ConfigReader? = null

    override fun processCompleted(method: ExplicitMethod, kv: KV<String, Any?>, request: Request) {
        super.processCompleted(method, kv, request)

        val tags = ruleComputer!!.computer(YapiClassExportRuleKeys.TAG, method)
        if (tags.notNullOrEmpty()) {
            request.setTags(StringUtils.split(tags, configReader!!.first("api.tag.delimiter") ?: ",\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() })
        }

        val status = ruleComputer.computer(YapiClassExportRuleKeys.STATUS, method)
        request.setStatus(status)

        val open = ruleComputer.computer(YapiClassExportRuleKeys.OPEN, method)
        request.setOpen(open)
    }

    override fun processMethodParameter(request: Request, parameter: ExplicitParameterInfo, typeObject: Any?, paramDesc: String?) {

        //RequestBody(json)
        if (isRequestBody(parameter.psi())) {
            requestHelper!!.setMethodIfMissed(request, HttpMethod.POST)
            requestHelper.addHeader(request, "Content-Type", "application/json")
            requestHelper.setJsonBody(
                    request,
                    typeObject,
                    paramDesc
            )
            return
        }

        //ModelAttr(form)
        if (isModelAttr(parameter.psi())) {
            if (request.method == HttpMethod.NO_METHOD) {
                requestHelper!!.setMethod(request,
                        ruleComputer!!.computer(ClassExportRuleKeys.METHOD_DEFAULT_HTTP_METHOD, parameter.containMethod())
                                ?: HttpMethod.POST)
            }
            if (request.method == HttpMethod.GET) {
                addParamAsQuery(parameter, request, typeObject)
            } else {
                addParamAsForm(parameter, request, typeObject, paramDesc)
            }
            return
        }

        var ultimateComment = (paramDesc ?: "")
        parameter.getType()?.let { duckType ->
            commentResolver!!.resolveCommentForType(duckType, parameter.psi())?.let {
                ultimateComment = "$ultimateComment $it"
            }
        }

        val demo = ruleComputer!!.computer(YapiClassExportRuleKeys.PARAM_DEMO, parameter)

        //head
        val requestHeaderAnn = findRequestHeader(parameter.psi())
        if (requestHeaderAnn != null) {

            var headName = requestHeaderAnn.any("value", "name")
            if (headName.anyIsNullOrEmpty()) {
                headName = parameter.name()
            }

            var required = findRequired(requestHeaderAnn)
            if (!required && ruleComputer.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) == true) {
                required = true
            }


            val defaultValue = findDefaultValue(requestHeaderAnn) ?: ""

            val header = Header()
            header.name = headName?.toString()
            header.value = defaultValue.toString()
            header.desc = ultimateComment
            header.required = required
            if (demo.notNullOrBlank()) {
                header.setDemo(demo)
            }
            requestHelper!!.addHeader(request, header)
            return
        }

        //path
        val pathVariableAnn = findPathVariable(parameter.psi())
        if (pathVariableAnn != null) {

            var pathName = pathVariableAnn["value"]?.toString()

            if (pathName == null) {
                pathName = parameter.name()
            }

            val pathParam = PathParam()
            pathParam.name = pathName
            pathParam.desc = ultimateComment
            if (demo.notNullOrBlank()) {
                pathParam.setDemo(demo)
            }
            requestHelper!!.addPathParam(request, pathParam)
            return
        }

        //cookie
        val cookieValueAnn = findCookieValue(parameter.psi())
        if (cookieValueAnn != null) {

            var cookieName = cookieValueAnn["value"]?.toString()

            if (cookieName == null) {
                cookieName = parameter.name()
            }

            var required = findRequired(cookieValueAnn)
            if (!required && ruleComputer.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter) == true) {
                required = true
            }

            requestHelper!!.appendDesc(request, if (required) {
                "\nNeed cookie:$cookieName ($ultimateComment)"
            } else {
                val defaultValue = findDefaultValue(cookieValueAnn)
                if (defaultValue.isNullOrBlank()) {
                    "\nCookie:$cookieName ($ultimateComment)"
                } else {
                    "\nCookie:$cookieName=$defaultValue ($ultimateComment)"
                }
            })

            return
        }

        //form/body/query
        var paramType: String? = null

        val requestParamAnn = findRequestParam(parameter.psi())

        if (requestParamAnn != null) {
            parameter.paramName = findParamName(requestParamAnn)
            parameter.required = findRequired(requestParamAnn)
            parameter.defaultVal = findDefaultValue(requestParamAnn)

            if (request.method == "GET") {
                paramType = "query"
            }
        }

        val readParamDefaultValue = readParamDefaultValue(parameter)

        if (readParamDefaultValue.notNullOrBlank()) {
            parameter.defaultVal = readParamDefaultValue
        }

        if (parameter.required == null) {
            parameter.required = ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter)
        }

        if (request.method == HttpMethod.GET) {
            addParamAsQuery(parameter, request, typeObject, ultimateComment)
                    .trySetDemo(demo)
            return
        }

        if (paramType.isNullOrBlank()) {
            paramType = ruleComputer.computer(ClassExportRuleKeys.PARAM_HTTP_TYPE,
                    parameter)
        }

        if (paramType.notNullOrBlank()) {
            when (paramType) {
                "body" -> {
                    requestHelper!!.setMethodIfMissed(request, HttpMethod.POST)
                    setRequestBody(request, typeObject, ultimateComment)
                            .trySetDemo(demo)
                    return
                }
                "form" -> {
                    requestHelper!!.setMethodIfMissed(request, HttpMethod.POST)
                    addParamAsForm(parameter, request, parameter.defaultVal ?: typeObject, ultimateComment)
                            .trySetDemo(demo)
                    return
                }
                "query" -> {
                    addParamAsQuery(parameter, request, parameter.defaultVal ?: typeObject, ultimateComment)
                            .trySetDemo(demo)
                    return
                }
                else -> {
                    logger!!.warn("Unknown param type:$paramType." +
                            "Return of rule `param.without.ann.type`" +
                            "should be `body/form/query`")
                }
            }
        }

        if (typeObject.hasFile()) {
            addParamAsForm(parameter, request, typeObject, ultimateComment)
                    .trySetDemo(demo)
            return
        }

        if (parameter.defaultVal != null) {
            requestHelper!!.addParam(request,
                    parameter.name()
                    , parameter.defaultVal.toString()
                    , parameter.required ?: false
                    , ultimateComment)
                    .trySetDemo(demo)
            return
        }

//        if (request.canHasForm()) {
//            addParamAsForm(parameter, request, typeObject, ultimateComment)
//                    .trySetDemo(demo)
//            return
//        }

        //else
        addParamAsQuery(parameter, request, typeObject, ultimateComment)
                .trySetDemo(demo)
    }

    @Suppress("UNCHECKED_CAST")
    override fun addParamAsQuery(parameter: ExplicitParameterInfo, request: Request, typeObject: Any?, paramDesc: String?): Any? {

        try {
            if (typeObject == Magics.FILE_STR) {
                return requestHelper!!.addFormFileParam(
                        request, parameter.name(),
                        parameter.required ?: ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter)
                        ?: false, paramDesc
                )
            } else if (typeObject != null && typeObject is Map<*, *>) {
                if (request.hasBodyOrForm() && formExpanded() && typeObject.isComplex()
                        && requestHelper!!.addHeaderIfMissed(request, "Content-Type", "multipart/form-data")) {
                    typeObject.flatValid(object : FieldConsumer {
                        override fun consume(parent: Map<*, *>?, path: String, key: String, value: Any?) {
                            val fv = deepComponent(value)
                            if (fv == Magics.FILE_STR) {
                                requestHelper.addFormFileParam(
                                        request, path,
                                        parent?.getAs<Boolean>(Attrs.REQUIRED_ATTR, key) ?: false,
                                        KVUtils.getUltimateComment(parent?.getAs(Attrs.COMMENT_ATTR), key)
                                ).setDemo(parent?.getAs(Attrs.DEMO_ATTR, key))
                            } else {
                                requestHelper.addFormParam(
                                        request, path, tinyQueryParam(value.toString()),
                                        parent?.getAs<Boolean>(Attrs.REQUIRED_ATTR, key) ?: false,
                                        KVUtils.getUltimateComment(parent?.getAs(Attrs.COMMENT_ATTR), key)
                                ).setDemo(parent?.getAs(Attrs.DEMO_ATTR, key))
                            }
                        }
                    })
                } else {
                    val fields = typeObject as KV<String, Any?>
                    val comment = fields.getAsKv(Attrs.COMMENT_ATTR)
                    val required = fields.getAsKv(Attrs.REQUIRED_ATTR)
                    val demo = fields.getAsKv(Attrs.DEMO_ATTR)
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
                            ).setDemo(demo?.getAs(filedName))
                        } else {
                            requestHelper!!.addParam(
                                    request, filedName, null,
                                    required?.getAs(filedName) ?: false,
                                    KVUtils.getUltimateComment(comment, filedName)
                            ).setDemo(demo?.getAs(filedName))
                        }
                    }
                }
            } else {
                return requestHelper!!.addParam(
                        request, parameter.name(), tinyQueryParam(typeObject?.toString()),
                        parameter.required ?: ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter)
                        ?: false, paramDesc
                )
            }
        } catch (e: Exception) {
            logger!!.traceError("error to parse[" + parameter.getType()?.canonicalText() + "] as Querys", e)
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    override fun addParamAsForm(parameter: ExplicitParameterInfo, request: Request, typeObject: Any?, paramDesc: String?): Any? {

        try {
            if (typeObject == Magics.FILE_STR) {
                return requestHelper!!.addFormFileParam(
                        request, parameter.name(),
                        parameter.required ?: ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter)
                        ?: false, paramDesc
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
                    val demo = fields.getAsKv(Attrs.DEMO_ATTR)
                    requestHelper!!.addHeaderIfMissed(request, "Content-Type", "application/x-www-form-urlencoded")
                    fields.forEachValid { filedName, fieldVal ->
                        val fv = deepComponent(fieldVal)
                        if (fv == Magics.FILE_STR) {
                            requestHelper.addFormFileParam(
                                    request, filedName,
                                    required?.getAs(filedName) ?: false,
                                    KVUtils.getUltimateComment(comment, filedName)
                            ).setDemo(demo?.getAs(filedName))
                        } else {
                            requestHelper.addFormParam(
                                    request, filedName, null,
                                    required?.getAs(filedName) ?: false,
                                    KVUtils.getUltimateComment(comment, filedName)
                            ).setDemo(demo?.getAs(filedName))
                        }
                    }
                }
            } else {
                return requestHelper!!.addFormParam(
                        request, parameter.name(), tinyQueryParam(typeObject?.toString()),
                        parameter.required ?: ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, parameter)
                        ?: false,
                        paramDesc
                )
            }
        } catch (e: Exception) {
            logger!!.traceError("error to parse[" + parameter.getType()?.canonicalText() + "] as ModelAttribute", e)
        }
        return null
    }

    override fun resolveParamStr(request: Request, params: String) {
        when {
            params.startsWith("!") -> {
                requestHelper!!.appendDesc(request, "parameter [${params.removeSuffix("!")}] should not be present")
            }
            params.contains("!=") -> {
                val name = params.substringBefore("!=").trim()
                val value = params.substringAfter("!=").trim()
                val param = request.querys?.find { it.name == name }
                if (param == null) {
                    requestHelper!!.appendDesc(request, "parameter [$name] " +
                            "should not equal to [$value]")
                } else {
                    param.desc = param.desc.append("should not equal to [$value]", "\n")
                }
            }
            !params.contains('=') -> {
                val param = request.querys?.find { it.name == params }
                if (enableUrlTemplating()) {
                    addParamToPath(request, params, "{$params}")
                    requestHelper!!.addPathParam(request, params, "", param?.desc)
                    param?.let { requestHelper.removeParam(request, it) }
                } else {
                    if (param == null) {
                        requestHelper!!.addParam(request, params, null, true, null)
                    } else {
                        param.required = true
                    }
                }
            }
            else -> {
                val name = params.substringBefore("=").trim()
                val value = params.substringAfter("=").trim()
                val param = request.querys?.find { it.name == name }

                if (enableUrlTemplating()) {
                    addParamToPath(request, name, value)
                    requestHelper!!.addPathParam(request, name, value, param?.desc)
                    param?.let { requestHelper.removeParam(request, it) }
                } else {
                    if (param == null) {
                        requestHelper!!.addParam(request, name, value, true, null)
                    } else {
                        param.required = true
                        param.value = value
                    }
                }
            }
        }
    }

    protected open fun addParamToPath(request: Request,
                                      paramName: String,
                                      value: String) {
        request.path = (request.path ?: URL.nil()).map { path ->
            if (path != null) {
                if (path.endsWith('?')) {
                    return@map "$path$paramName=$value"
                } else if (path.contains('?')) {
                    return@map "$path&$paramName=$value"
                }
            }
            "$path?$paramName=$value"
        }
    }

    protected fun enableUrlTemplating(): Boolean {
        return settingBinder!!.read().enableUrlTemplating
    }

    private fun Any?.trySetDemo(demo: String?) {
        (this as? Extensible)?.setDemo(demo)
    }
}