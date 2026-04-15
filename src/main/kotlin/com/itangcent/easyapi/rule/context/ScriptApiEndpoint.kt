package com.itangcent.easyapi.rule.context

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod

class ScriptApiEndpoint(val endpoint: ApiEndpoint) {

    private val http: HttpMetadata? get() = endpoint.metadata as? HttpMetadata

    fun name(): String? = endpoint.name

    fun path(): String? = http?.path

    fun setPath(path: String) {
        val meta = http ?: return
        meta.path = path
    }

    fun method(): String? = http?.method?.name

    fun setMethod(method: String) {
        val meta = http ?: return
        HttpMethod.values().find { it.name.equals(method, ignoreCase = true) }?.let {
            meta.method = it
        }
    }

    fun description(): String? = endpoint.description

    fun setDescription(desc: String?) {
        endpoint.description = desc
    }

    fun setParam(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        endpoint.setParam(name, defaultValue, required, desc)
    }

    fun setFormParam(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        endpoint.setFormParam(name, defaultValue, required, desc)
    }

    fun setPathParam(name: String?, defaultValue: String?, desc: String?) {
        endpoint.setPathParam(name, defaultValue, desc)
    }

    fun setHeader(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        endpoint.setHeader(name, defaultValue, required, desc)
    }

    fun setResponseCode(code: Int) {
        endpoint.setResponseCode(code)
    }

    fun appendResponseBodyDesc(desc: String?) {
        endpoint.appendResponseBodyDesc(desc)
    }

    fun setResponseHeader(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        endpoint.setResponseHeader(name, defaultValue, required, desc)
    }

    fun setResponseBodyClass(className: String?) {
        endpoint.setResponseBodyClass(className)
    }

    fun appendDesc(desc: String?) {
        endpoint.appendDesc(desc)
    }

    override fun toString(): String = endpoint.toString()
}