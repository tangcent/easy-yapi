package com.itangcent.idea.plugin.api.export.rule

import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.common.model.*
import com.itangcent.common.utils.anyIsNullOrBlank
import com.itangcent.common.utils.append
import com.itangcent.common.utils.asInt
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.plugin.api.export.*
import com.itangcent.idea.psi.resource
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.DuckTypeHelper
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.JsonOption

class RequestRuleWrap(private val request: Request) {

    fun setName(name: String) {
        requestHelper.setName(request, name)
    }

    fun setMethod(method: String) {
        requestHelper.setMethod(request, method)
    }

    fun setPath(path: URL) {
        requestHelper.setPath(request, path)
    }

    /**
     * addAsJsonBody if content-type is json
     * otherwise addAsForm
     */
    fun setModelAsBody(model: Any) {
        requestHelper.setModelAsBody(request, model)
    }

    fun addModelAsParam(model: Any) {
        requestHelper.addModelAsParam(request, model)
    }

    fun addFormParam(formParam: FormParam) {
        requestHelper.addFormParam(request, formParam)
    }

    fun addParam(param: Param) {
        requestHelper.addParam(request, param)
    }

    fun addPathParam(pathParam: PathParam) {
        requestHelper.addPathParam(request, pathParam)
    }

    fun setJsonBody(body: Any?, bodyAttr: String?) {
        requestHelper.setJsonBody(request, body, bodyAttr)
    }

    fun appendDesc(desc: String?) {
        requestHelper.appendDesc(request, desc)
    }

    fun addHeader(header: Header) {
        requestHelper.addHeader(request, header)
    }

    //region response

    @ScriptIgnore
    private fun response(): Response? {
        return request.response?.first()
    }

    fun addResponseHeader(header: Header) {
        response()?.let { requestHelper.addResponseHeader(it, header) }
    }

    fun setResponseBody(bodyType: String, body: Any?) {
        response()?.let { requestHelper.setResponseBody(it, bodyType, body) }
    }

    fun setResponseBody(body: Any?) {
        response()?.let { requestHelper.setResponseBody(it, "raw", body) }
    }

    fun setResponseBodyClass(bodyClass: String?) {
        if (bodyClass == null) {
            return
        }
        response()?.let {
            val context = ActionContext.getContext()!!
            val resource = request.resource()
            if (resource == null) {
                context.instance(Logger::class).warn("no resource be related with:${request}")
                return
            }
            val responseType = context.instance(DuckTypeHelper::class).findType(bodyClass, resource)
            val res = context.instance(PsiClassHelper::class).getTypeObject(responseType, resource, JsonOption.ALL)
            requestHelper.setResponseBody(it, "raw", res)
        }
    }

    fun setResponseBodyClass(bodyType: String, bodyClass: String?) {
        if (bodyClass == null) {
            return
        }
        response()?.let {
            val context = ActionContext.getContext()!!
            val resource = request.resource()
            if (resource == null) {
                context.instance(Logger::class).warn("no resource be related with:${request}")
                return
            }
            val responseType = context.instance(DuckTypeHelper::class).findType(bodyClass, resource)
            val res = context.instance(PsiClassHelper::class).getTypeObject(responseType, resource, JsonOption.ALL)
            requestHelper.setResponseBody(it, bodyType, res)
        }
    }

    fun setResponseCode(code: Any?) {
        val codeInt = code.asInt() ?: return
        response()?.let { requestHelper.setResponseCode(it, codeInt) }
    }

    fun appendResponseBodyDesc(bodyDesc: String?) {
        response()?.let { requestHelper.appendResponseBodyDesc(it, bodyDesc) }
    }

    fun addResponseHeader(name: String, value: String) {
        response()?.let { requestHelper.addResponseHeader(it, name, value) }
    }

    fun addResponseHeader(name: String, value: String?, required: Boolean?, desc: String?) {
        val response = response() ?: return
        val header = Header()
        header.name = name
        header.value = value
        header.required = required
        header.desc = desc
        requestHelper.addResponseHeader(response, header)
    }

    fun setResponseHeader(name: String, value: String?, required: Boolean?, desc: String?) {
        val response = response() ?: return
        val header = response.headers?.firstOrNull { it.name == name }
        if (header == null) {
            addResponseHeader(name, value, required, desc)
        } else {
            if (header.value.anyIsNullOrBlank() && value.notNullOrBlank()) {
                header.value = value
            }
            header.desc = header.desc.append(desc)
            header.required = required
        }
    }

    fun addParam(paramName: String, defaultVal: String?, desc: String?) {
        requestHelper.addParam(request, paramName, defaultVal, desc)
    }

    fun addParam(paramName: String, defaultVal: String?, required: Boolean?, desc: String?) {
        requestHelper.addParam(request, paramName, defaultVal, required ?: true, desc)
    }

    fun setParam(paramName: String, defaultVal: String?, required: Boolean?, desc: String?) {
        val param = request.querys?.firstOrNull { it.name == paramName }
        if (param == null) {
            requestHelper.addParam(request, paramName, defaultVal, required ?: true, desc)
        } else {
            if (param.value.anyIsNullOrBlank() && defaultVal.notNullOrBlank()) {
                param.value = defaultVal
            }
            param.desc = param.desc.append(desc)
            param.required = required
        }
    }

    fun addFormParam(paramName: String, defaultVal: String?, desc: String?) {
        requestHelper.addFormParam(request, paramName, defaultVal, desc)
    }

    fun addFormParam(paramName: String, defaultVal: String?, required: Boolean?, desc: String?) {
        requestHelper.addFormParam(request, paramName, defaultVal, required ?: true, desc)
    }

    fun setFormParam(paramName: String, defaultVal: String?, required: Boolean?, desc: String?) {
        val param = request.formParams?.firstOrNull { it.name == paramName }
        if (param == null) {
            requestHelper.addFormParam(request, paramName, defaultVal, required ?: true, desc)
        } else {
            if (param.value.anyIsNullOrBlank() && defaultVal.notNullOrBlank()) {
                param.value = defaultVal
            }
            param.desc = param.desc.append(desc)
            param.required = required
        }
    }

    fun addFormFileParam(paramName: String, required: Boolean?, desc: String?) {
        requestHelper.addFormFileParam(request, paramName, required ?: true, desc)
    }

    fun setFormFileParam(paramName: String, required: Boolean?, desc: String?) {
        val param = request.formParams?.firstOrNull { it.name == paramName }
        if (param == null) {
            requestHelper.addFormFileParam(request, paramName, required ?: true, desc)
        } else {
            param.desc = param.desc.append(desc)
            param.required = required
        }
    }

    fun addHeader(name: String, value: String) {
        requestHelper.addHeader(request, name, value)
    }

    fun addHeaderIfMissed(name: String, value: String): Boolean {
        return requestHelper.addHeaderIfMissed(request, name, value)
    }

    fun addHeader(name: String, value: String?, required: Boolean?, desc: String?) {
        val header = Header()
        header.name = name
        header.value = value
        header.required = required
        header.desc = desc
        requestHelper.addHeader(request, header)
    }

    fun setHeader(name: String, value: String?, required: Boolean?, desc: String?) {
        val header = request.headers?.firstOrNull { it.name == name }
        if (header == null) {
            addHeader(name, value, required, desc)
        } else {
            if (header.value.anyIsNullOrBlank() && value.notNullOrBlank()) {
                header.value = value
            }
            header.desc = header.desc.append(desc)
            header.required = required
        }
    }

    fun addPathParam(name: String, desc: String) {
        requestHelper.addPathParam(request, name, desc)
    }

    fun addPathParam(name: String, value: String, desc: String) {
        val pathParam = PathParam()
        pathParam.name = name
        pathParam.value = value
        pathParam.desc = desc
        requestHelper.addPathParam(request, pathParam)
    }

    fun setPathParam(name: String, value: String, desc: String) {
        val param = request.paths?.firstOrNull { it.name == name }
        if (param == null) {
            addPathParam(name, value, desc)
        } else {
            if (param.value.anyIsNullOrBlank() && value.notNullOrBlank()) {
                param.value = value
            }
            param.desc = param.desc.append(desc)
        }
    }

    fun setMethodIfMissed(method: String) {
        return requestHelper.setMethodIfMissed(request, method)
    }

    companion object {
        val requestHelper: RequestHelper = ActionContext.local()
    }
}