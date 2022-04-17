package com.itangcent.idea.plugin.api.export.rule

import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.model.*
import com.itangcent.common.utils.*
import com.itangcent.idea.plugin.api.export.*
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.psi.resource
import com.itangcent.idea.utils.setExts
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.DuckTypeHelper
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.jvm.JsonOption
import java.util.*

class RequestRuleWrap(private val methodExportContext: MethodExportContext?, private val request: Request) {

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
            context.instance(Logger::class).warn("no resource be related with:${request}")
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

    fun addModelClass(modelClass: String?) {
        if (modelClass == null) {
            return
        }
        val context = ActionContext.getContext()!!
        val resource = request.resource()
        if (resource == null) {
            context.instance(Logger::class).warn("no resource be related with:${request}")
            return
        }
        val responseType = context.instance(DuckTypeHelper::class).findType(modelClass, resource)
        val res = context.instance(PsiClassHelper::class).getTypeObject(responseType, resource, JsonOption.ALL)
        res?.let { requestBuilderListener.addModelAsParam(methodExportContext!!, this.request, it) }
    }

    fun addFormParam(formParam: FormParam) {
        requestBuilderListener.addFormParam(methodExportContext!!, request, formParam)
    }

    fun addParam(param: Param) {
        requestBuilderListener.addParam(methodExportContext!!, request, param)
    }

    fun addPathParam(pathParam: PathParam) {
        requestBuilderListener.addPathParam(methodExportContext!!, request, pathParam)
    }

    fun setJsonBody(body: Any?, bodyAttr: String?) {
        requestBuilderListener.setJsonBody(methodExportContext!!, request, body, bodyAttr)
    }

    fun addParam(paramName: String, defaultVal: String?, desc: String?) {
        requestBuilderListener.addParam(methodExportContext!!, request, paramName, defaultVal, desc)
    }

    fun addParam(paramName: String, defaultVal: String?, required: Boolean?, desc: String?) {
        requestBuilderListener.addParam(methodExportContext!!, request, paramName, defaultVal, required ?: true, desc)
    }

    fun setParam(paramName: String, defaultVal: String?, required: Boolean?, desc: String?) {
        val param = request.querys?.firstOrNull { it.name == paramName }
        if (param == null) {
            requestBuilderListener.addParam(methodExportContext!!,
                request,
                paramName,
                defaultVal,
                required ?: true,
                desc)
        } else {
            param.value = defaultVal
            param.desc = desc
            param.required = required
        }
    }

    fun addFormParam(paramName: String, defaultVal: String?, desc: String?) {
        requestBuilderListener.addFormParam(methodExportContext!!, request, paramName, defaultVal, desc)
    }

    fun addFormParam(paramName: String, defaultVal: String?, required: Boolean?, desc: String?) {
        requestBuilderListener.addFormParam(methodExportContext!!,
            request,
            paramName,
            defaultVal,
            required ?: true,
            desc)
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

    fun addHeader(name: String, value: String) {
        requestBuilderListener.addHeader(methodExportContext!!, request, name, value)
    }

    fun addHeaderIfMissed(name: String, value: String): Boolean {
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
            header.desc = header.desc.append(desc)
            header.required = required
        }
    }

    fun addPathParam(name: String, desc: String) {
        requestBuilderListener.addPathParam(methodExportContext!!, request, name, desc)
    }

    fun addPathParam(name: String, value: String, desc: String) {
        val pathParam = PathParam()
        pathParam.name = name
        pathParam.value = value
        pathParam.desc = desc
        requestBuilderListener.addPathParam(methodExportContext!!, request, pathParam)
    }

    fun setPathParam(name: String, value: String, desc: String) {
        val param = request.paths?.firstOrNull { it.name == name }
        if (param == null) {
            addPathParam(name, value, desc)
        } else {
            param.value = value
            param.desc = param.desc.append(desc)
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
            context.instance(Logger::class).warn("no resource be related with:${request}")
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
            context.instance(Logger::class).warn("no resource be related with:${request}")
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

    companion object {
        val requestBuilderListener: RequestBuilderListener = ActionContext.local()
    }
}

class MethodDocRuleWrap(private val methodExportContext: MethodExportContext?, private val methodDoc: MethodDoc) {

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

    companion object {
        val methodDocBuilderListener: MethodDocBuilderListener = ActionContext.local()
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