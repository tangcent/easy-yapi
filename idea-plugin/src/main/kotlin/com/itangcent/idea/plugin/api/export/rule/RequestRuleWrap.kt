package com.itangcent.idea.plugin.api.export.rule

import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.model.*
import com.itangcent.common.utils.asInt
import com.itangcent.common.utils.mapToTypedArray
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.psi.resource
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.logger
import com.itangcent.intellij.jvm.DuckTypeHelper
import com.itangcent.intellij.jvm.JsonOption
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.utils.setExts
import java.util.*

class RequestRuleWrap(private val methodExportContext: MethodExportContext?, private val request: Request) {

    private val requestBuilderListener: RequestBuilderListener by lazy { ActionContext.local() }

    //region request

    fun name(): String? {
        return request.name
    }

    fun setName(name: String) {
        requestBuilderListener.setName(methodExportContext!!, request, name)
    }

    fun desc(): String? {
        return request.desc
    }

    fun setDesc(desc: String?) {
        request.desc = desc
    }

    fun appendDesc(desc: String?) {
        requestBuilderListener.appendDesc(methodExportContext!!, request, desc)
    }

    /**
     * The HTTP method.
     *
     * @see HttpMethod
     */
    fun method(): String? {
        return request.method
    }

    fun setMethod(method: String) {
        requestBuilderListener.setMethod(methodExportContext!!, request, method)
    }

    fun setMethodIfMissed(method: String) {
        return requestBuilderListener.setMethodIfMissed(methodExportContext!!, request, method)
    }

    fun path(): String? {
        return request.path?.url()
    }

    fun paths(): Array<String>? {
        return request.path?.urls()
    }

    fun setPath(vararg path: String?) {
        requestBuilderListener.setPath(methodExportContext!!, request, URL.of(*path))
    }

    fun setPaths(path: List<String>) {
        requestBuilderListener.setPath(methodExportContext!!, request, URL.of(path))
    }

    /**
     * raw/json/xml
     */
    fun bodyType(): String? {
        return request.bodyType
    }

    /**
     * The description of [body] if it is present.
     */
    fun bodyAttr(): String? {
        return request.bodyAttr
    }

    /**
     * addAsJsonBody if content-type is json
     * otherwise addAsForm
     */
    fun setBody(model: Any?) {
        model?.let { requestBuilderListener.setModelAsBody(methodExportContext!!, request, it) }
    }

    fun setBodyClass(bodyClass: String?) {
        if (bodyClass == null) {
            return
        }
        val context = ActionContext.getContext()!!
        val resource = request.resource()
        if (resource == null) {
            context.logger().warn("no resource be related with:${request}")
            return
        }
        val responseType = context.instance(DuckTypeHelper::class).findType(bodyClass, resource) ?: return
        val res =
            context.instance(PsiClassHelper::class).getTypeObject(responseType, resource, JsonOption.ALL) ?: return
        requestBuilderListener.setModelAsBody(methodExportContext!!, this.request, res)
    }

    fun addModelAsParam(model: Any) {
        requestBuilderListener.addModelAsParam(methodExportContext!!, request, model)
    }

    fun addModelAsFormParam(model: Any) {
        requestBuilderListener.addModelAsFormParam(methodExportContext!!, request, model)
    }

    fun addModelClassAsParam(modelClass: String?) {
        if (modelClass == null) {
            return
        }
        val context = ActionContext.getContext()!!
        val resource = request.resource()
        if (resource == null) {
            context.logger().warn("no resource be related with:${request}")
            return
        }
        val responseType = context.instance(DuckTypeHelper::class).findType(modelClass, resource)
        val res = context.instance(PsiClassHelper::class).getTypeObject(responseType, resource, JsonOption.ALL)
        res?.let { requestBuilderListener.addModelAsParam(methodExportContext!!, this.request, it) }
    }

    fun addModelClassAsFormParam(modelClass: String?) {
        if (modelClass == null) {
            return
        }
        val context = ActionContext.getContext()!!
        val resource = request.resource()
        if (resource == null) {
            context.logger().warn("no resource be related with:${request}")
            return
        }
        val responseType = context.instance(DuckTypeHelper::class).findType(modelClass, resource)
        val res = context.instance(PsiClassHelper::class).getTypeObject(responseType, resource, JsonOption.ALL)
        res?.let { requestBuilderListener.addModelAsFormParam(methodExportContext!!, this.request, it) }
    }

    @Deprecated(
        message = "use addModelClassAsFormParam instead",
        replaceWith = ReplaceWith("addModelClassAsFormParam(modelClass)")
    )
    fun addModelClass(modelClass: String?) {
        ActionContext.getContext()
            ?.logger()
            ?.warn("addModelClass is deprecated, please use addModelClassAsFormParam instead of addModelClass")
        addModelClassAsFormParam(modelClass)
    }

    fun setJsonBody(body: Any?, bodyAttr: String?) {
        requestBuilderListener.setJsonBody(methodExportContext!!, request, body, bodyAttr)
    }

    // Header methods
    fun headers(): Array<HeaderRuleWrap> {
        return this.request.headers?.mapToTypedArray { HeaderRuleWrap(this.request, it) } ?: emptyArray()
    }

    fun header(name: String): HeaderRuleWrap? {
        return this.request.headers
            ?.firstOrNull { it.name == name }
            ?.let { HeaderRuleWrap(this.request, it) }
    }

    fun headers(name: String): Array<HeaderRuleWrap> {
        return this.request.headers
            ?.filter { it.name == name }
            ?.mapToTypedArray { HeaderRuleWrap(this.request, it) }
            ?: emptyArray()
    }

    fun removeHeader(name: String) {
        this.request.headers?.removeIf { it.name == name }
    }

    fun addHeader(name: String, value: String?) {
        requestBuilderListener.addHeader(methodExportContext!!, request, name, value)
    }

    fun addHeaderIfMissed(name: String, value: String?): Boolean {
        return requestBuilderListener.addHeaderIfMissed(methodExportContext!!, request, name, value)
    }

    fun addHeader(name: String, value: String?, required: Boolean?, desc: String?) {
        val header = Header()
        header.name = name
        header.value = value
        header.required = required
        header.desc = desc
        requestBuilderListener.addHeader(methodExportContext!!, request, header)
    }

    fun setHeader(name: String, value: String?, required: Boolean?, desc: String?) {
        val header = request.headers?.firstOrNull { it.name == name }
        if (header == null) {
            addHeader(name, value, required, desc)
        } else {
            header.value = value
            header.desc = desc
            header.required = required
        }
    }

    // Query parameter methods
    fun params(): Array<ParamRuleWrap>? {
        return request.querys?.map { ParamRuleWrap(request, it) }?.toTypedArray()
    }

    fun param(name: String): ParamRuleWrap? {
        return request.querys?.firstOrNull { it.name == name }?.let { ParamRuleWrap(request, it) }
    }

    fun params(name: String): Array<ParamRuleWrap>? {
        return request.querys?.filter { it.name == name }?.map { ParamRuleWrap(request, it) }?.toTypedArray()
    }

    fun addParam(param: Param) {
        requestBuilderListener.addParam(methodExportContext!!, request, param)
    }

    fun addParam(paramName: String, defaultVal: Any?, desc: String?) {
        requestBuilderListener.addParam(methodExportContext!!, request, paramName, defaultVal, desc)
    }

    fun addParam(paramName: String, defaultVal: Any?, required: Boolean?, desc: String?) {
        requestBuilderListener.addParam(methodExportContext!!, request, paramName, defaultVal, required ?: true, desc)
    }

    fun setParam(paramName: String, defaultVal: Any?, required: Boolean?, desc: String?) {
        val param = request.querys?.firstOrNull { it.name == paramName }
        if (param == null) {
            requestBuilderListener.addParam(
                methodExportContext!!,
                request,
                paramName,
                defaultVal,
                required ?: true,
                desc
            )
        } else {
            param.value = defaultVal
            param.desc = desc
            param.required = required
        }
    }

    // Form parameter methods
    fun formParams(): Array<FormParamRuleWrap>? {
        return request.formParams?.map { FormParamRuleWrap(request, it) }?.toTypedArray()
    }

    fun formParam(name: String): FormParamRuleWrap? {
        return request.formParams?.firstOrNull { it.name == name }?.let { FormParamRuleWrap(request, it) }
    }

    fun formParams(name: String): Array<FormParamRuleWrap>? {
        return request.formParams?.filter { it.name == name }?.map { FormParamRuleWrap(request, it) }?.toTypedArray()
    }

    fun addFormParam(formParam: FormParam) {
        requestBuilderListener.addFormParam(methodExportContext!!, request, formParam)
    }

    fun addFormParam(paramName: String, defaultVal: String?, desc: String?) {
        requestBuilderListener.addFormParam(methodExportContext!!, request, paramName, defaultVal, desc)
    }

    fun addFormParam(paramName: String, defaultVal: String?, required: Boolean?, desc: String?) {
        requestBuilderListener.addFormParam(
            methodExportContext!!,
            request,
            paramName,
            defaultVal,
            required ?: true,
            desc
        )
    }

    fun setFormParam(paramName: String, defaultVal: String?, required: Boolean?, desc: String?) {
        val param = request.formParams?.firstOrNull { it.name == paramName }
        if (param == null) {
            requestBuilderListener.addFormParam(
                methodExportContext!!, request, paramName, defaultVal, required
                    ?: true, desc
            )
        } else {
            param.value = defaultVal
            param.desc = desc
            param.required = required
        }
    }

    fun addFormFileParam(paramName: String, required: Boolean?, desc: String?) {
        requestBuilderListener.addFormFileParam(methodExportContext!!, request, paramName, required ?: true, desc)
    }

    fun setFormFileParam(paramName: String, required: Boolean?, desc: String?) {
        val param = request.formParams?.firstOrNull { it.name == paramName }
        if (param == null) {
            requestBuilderListener.addFormFileParam(methodExportContext!!, request, paramName, required ?: true, desc)
        } else {
            param.desc = desc
            param.required = required
        }
    }

    // Path parameter methods
    fun pathParams(): Array<PathParamRuleWrap>? {
        return request.paths?.map { PathParamRuleWrap(request, it) }?.toTypedArray()
    }

    fun pathParam(name: String): PathParamRuleWrap? {
        return request.paths?.firstOrNull { it.name == name }?.let { PathParamRuleWrap(request, it) }
    }

    fun pathParams(name: String): Array<PathParamRuleWrap>? {
        return request.paths?.filter { it.name == name }?.map { PathParamRuleWrap(request, it) }?.toTypedArray()
    }

    fun addPathParam(pathParam: PathParam) {
        requestBuilderListener.addPathParam(methodExportContext!!, request, pathParam)
    }

    fun addPathParam(name: String, desc: String?) {
        requestBuilderListener.addPathParam(methodExportContext!!, request, name, desc)
    }

    fun addPathParam(name: String, value: String?, desc: String?) {
        val pathParam = PathParam()
        pathParam.name = name
        pathParam.value = value
        pathParam.desc = desc
        requestBuilderListener.addPathParam(methodExportContext!!, request, pathParam)
    }

    fun setPathParam(name: String, value: String?, desc: String?) {
        val param = request.paths?.firstOrNull { it.name == name }
        if (param == null) {
            addPathParam(name, value, desc)
        } else {
            param.value = value
            param.desc = desc
        }
    }

    //endregion

    //region response

    @ScriptIgnore
    private fun response(): Response {
        if (request.response == null) {
            request.response = LinkedList()
            request.response!!.add(Response())
        }
        return request.response!!.first()
    }

    fun addResponseHeader(header: Header) {
        requestBuilderListener.addResponseHeader(methodExportContext!!, response(), header)
    }

    fun setResponseBody(bodyType: String, body: Any?) {
        requestBuilderListener.setResponseBody(methodExportContext!!, response(), bodyType, body)
    }

    fun setResponseBody(body: Any?) {
        requestBuilderListener.setResponseBody(methodExportContext!!, response(), "raw", body)
    }

    fun setResponseBodyClass(bodyClass: String?) {
        if (bodyClass == null) {
            return
        }
        val context = ActionContext.getContext()!!
        val resource = request.resource()
        if (resource == null) {
            context.logger().warn("no resource be related with:${request}")
            return
        }
        val responseType = context.instance(DuckTypeHelper::class).findType(bodyClass, resource)
        val res = context.instance(PsiClassHelper::class).getTypeObject(responseType, resource, JsonOption.ALL)
        requestBuilderListener.setResponseBody(methodExportContext!!, response(), "raw", res)
    }

    fun setResponseBodyClass(bodyType: String, bodyClass: String?) {
        if (bodyClass == null) {
            return
        }
        val context = ActionContext.getContext()!!
        val resource = request.resource()
        if (resource == null) {
            context.logger().warn("no resource be related with:${request}")
            return
        }
        val responseType = context.instance(DuckTypeHelper::class).findType(bodyClass, resource)
        val res = context.instance(PsiClassHelper::class).getTypeObject(responseType, resource, JsonOption.ALL)
        requestBuilderListener.setResponseBody(methodExportContext!!, response(), bodyType, res)
    }

    fun setResponseCode(code: Any?) {
        val codeInt = code.asInt() ?: return
        requestBuilderListener.setResponseCode(methodExportContext!!, response(), codeInt)
    }

    fun appendResponseBodyDesc(bodyDesc: String?) {
        requestBuilderListener.appendResponseBodyDesc(methodExportContext!!, response(), bodyDesc)
    }

    fun addResponseHeader(name: String, value: String) {
        requestBuilderListener.addResponseHeader(methodExportContext!!, response(), name, value)
    }

    fun addResponseHeader(name: String, value: String?, required: Boolean?, desc: String?) {
        val header = Header()
        header.name = name
        header.value = value
        header.required = required
        header.desc = desc
        requestBuilderListener.addResponseHeader(methodExportContext!!, response(), header)
    }

    fun setResponseHeader(name: String, value: String?, required: Boolean?, desc: String?) {
        val response = response()
        val header = response.headers?.firstOrNull { it.name == name }
        if (header == null) {
            addResponseHeader(name, value, required, desc)
        } else {
            header.value = value
            header.desc = desc
            header.required = required
        }
    }

    //endregion response
}

class MethodDocRuleWrap(private val methodExportContext: MethodExportContext?, private val methodDoc: MethodDoc) {

    private val methodDocBuilderListener: MethodDocBuilderListener by lazy { ActionContext.local() }

    fun name(): String? {
        return methodDoc.name
    }

    fun setName(name: String) {
        methodDocBuilderListener.setName(methodExportContext!!, methodDoc, name)
    }

    fun desc(): String? {
        return methodDoc.desc
    }

    fun setDesc(desc: String?) {
        methodDoc.desc = desc
    }

    fun appendDesc(desc: String?) {
        methodDocBuilderListener.appendDesc(methodExportContext!!, methodDoc, desc)
    }

    fun addParam(
        exportContext: ExportContext,
        methodDoc: MethodDoc, paramName: String, value: Any?, required: Boolean, desc: String?,
    ) {
        methodDocBuilderListener.addParam(methodExportContext!!, methodDoc, paramName, value, required, desc)
    }


    fun setRet(ret: Any?) {
        methodDocBuilderListener.setRet(methodExportContext!!, methodDoc, ret)
    }

    fun appendRetDesc(retDesc: String?) {
        methodDocBuilderListener.appendRetDesc(methodExportContext!!, methodDoc, retDesc)
    }
}

@ScriptTypeName("header")
class HeaderRuleWrap(
    private val request: Request,
    private val header: Header,
) {

    fun name(): String? {
        return header.name
    }

    fun setName(name: String?) {
        header.name = name
    }

    fun value(): String? {
        return header.value
    }

    fun setValue(value: String?) {
        header.value = value
    }

    fun desc(): String? {
        return header.desc
    }

    fun setDesc(desc: String?) {
        header.desc = desc
    }

    fun required(): Boolean? {
        return header.required
    }

    fun setRequired(required: Boolean?) {
        header.required = required
    }

    fun remove() {
        request.headers?.remove(this.header)
    }

    fun copy(): HeaderRuleWrap {
        val header = Header()
        header.name = this.header.name
        header.desc = this.header.desc
        header.value = this.header.value
        header.required = this.header.required
        this.header.exts()?.let { header.setExts(it) }
        return HeaderRuleWrap(request, header)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HeaderRuleWrap

        if (request != other.request) return false
        if (header != other.header) return false

        return true
    }

    override fun hashCode(): Int {
        var result = request.hashCode()
        result = 31 * result + header.hashCode()
        return result
    }
}

@ScriptTypeName("param")
class ParamRuleWrap(
    private val request: Request,
    private val param: Param,
) {

    fun name(): String? {
        return param.name
    }

    fun setName(name: String?) {
        param.name = name
    }

    fun value(): Any? {
        return param.value
    }

    fun setValue(value: Any?) {
        param.value = value
    }

    fun desc(): String? {
        return param.desc
    }

    fun setDesc(desc: String?) {
        param.desc = desc
    }

    fun required(): Boolean? {
        return param.required
    }

    fun setRequired(required: Boolean?) {
        param.required = required
    }

    fun remove() {
        request.querys?.remove(this.param)
    }

    fun copy(): ParamRuleWrap {
        val param = Param()
        param.name = this.param.name
        param.desc = this.param.desc
        param.value = this.param.value
        param.required = this.param.required
        this.param.exts()?.let { param.setExts(it) }
        return ParamRuleWrap(request, param)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ParamRuleWrap

        if (request != other.request) return false
        if (param != other.param) return false

        return true
    }

    override fun hashCode(): Int {
        var result = request.hashCode()
        result = 31 * result + param.hashCode()
        return result
    }
}

@ScriptTypeName("formParam")
class FormParamRuleWrap(
    private val request: Request,
    private val formParam: FormParam,
) {

    fun name(): String? {
        return formParam.name
    }

    fun setName(name: String?) {
        formParam.name = name
    }

    fun value(): String? {
        return formParam.value
    }

    fun setValue(value: String?) {
        formParam.value = value
    }

    fun desc(): String? {
        return formParam.desc
    }

    fun setDesc(desc: String?) {
        formParam.desc = desc
    }

    fun required(): Boolean? {
        return formParam.required
    }

    fun setRequired(required: Boolean?) {
        formParam.required = required
    }

    fun type(): String? {
        return formParam.type
    }

    fun setType(type: String?) {
        formParam.type = type
    }

    fun remove() {
        request.formParams?.remove(this.formParam)
    }

    fun copy(): FormParamRuleWrap {
        val formParam = FormParam()
        formParam.name = this.formParam.name
        formParam.desc = this.formParam.desc
        formParam.value = this.formParam.value
        formParam.required = this.formParam.required
        formParam.type = this.formParam.type
        this.formParam.exts()?.let { formParam.setExts(it) }
        return FormParamRuleWrap(request, formParam)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FormParamRuleWrap

        if (request != other.request) return false
        if (formParam != other.formParam) return false

        return true
    }

    override fun hashCode(): Int {
        var result = request.hashCode()
        result = 31 * result + formParam.hashCode()
        return result
    }
}

@ScriptTypeName("pathParam")
class PathParamRuleWrap(
    private val request: Request,
    private val pathParam: PathParam,
) {

    fun name(): String? {
        return pathParam.name
    }

    fun setName(name: String?) {
        pathParam.name = name
    }

    fun value(): String? {
        return pathParam.value
    }

    fun setValue(value: String?) {
        pathParam.value = value
    }

    fun desc(): String? {
        return pathParam.desc
    }

    fun setDesc(desc: String?) {
        pathParam.desc = desc
    }

    fun remove() {
        request.paths?.remove(this.pathParam)
    }

    fun copy(): PathParamRuleWrap {
        val pathParam = PathParam()
        pathParam.name = this.pathParam.name
        pathParam.desc = this.pathParam.desc
        pathParam.value = this.pathParam.value
        this.pathParam.exts()?.let { pathParam.setExts(it) }
        return PathParamRuleWrap(request, pathParam)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PathParamRuleWrap

        if (request != other.request) return false
        if (pathParam != other.pathParam) return false

        return true
    }

    override fun hashCode(): Int {
        var result = request.hashCode()
        result = 31 * result + pathParam.hashCode()
        return result
    }
}