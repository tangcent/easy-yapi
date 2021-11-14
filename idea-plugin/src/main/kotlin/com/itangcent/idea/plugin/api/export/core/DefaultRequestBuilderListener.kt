package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Singleton
import com.itangcent.common.constant.Attrs
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.model.*
import com.itangcent.common.utils.appendln
import com.itangcent.intellij.extend.toPrettyString
import java.util.*

@Singleton
open class DefaultRequestBuilderListener : RequestBuilderListener {
    override fun setName(exportContext: ExportContext, request: Request, name: String) {
        request.name = name
    }

    override fun setMethod(exportContext: ExportContext, request: Request, method: String) {
        request.method = method
    }

    override fun setPath(exportContext: ExportContext, request: Request, path: URL) {
        request.path = path
    }

    override fun setModelAsBody(exportContext: ExportContext, request: Request, model: Any) {
        request.body = model
    }

    override fun addModelAsParam(exportContext: ExportContext, request: Request, model: Any) {
        if (model is Map<*, *>) {
            val comment = model[Attrs.COMMENT_ATTR] as Map<*, *>?
            val default = model[Attrs.DEFAULT_VALUE_ATTR] as Map<*, *>?
            model.forEach { (k, v) ->
                addFormParam(
                    exportContext, request, k.toString(), (default?.get(k) ?: v).toPrettyString(),
                    KVUtils.getUltimateComment(comment, k)
                )
            }
        }
    }

    override fun addFormParam(exportContext: ExportContext, request: Request, formParam: FormParam) {
        if (request.formParams == null) {
            request.formParams = LinkedList()
        }
        request.formParams!!.add(formParam)
    }

    override fun addParam(exportContext: ExportContext, request: Request, param: Param) {
        if (request.querys == null) {
            request.querys = LinkedList()
        }
        request.querys!!.add(param)
    }

    override fun removeParam(exportContext: ExportContext, request: Request, param: Param) {
        request.querys?.remove(param)
    }

    override fun addPathParam(exportContext: ExportContext, request: Request, pathParam: PathParam) {
        if (request.paths == null) {
            request.paths = LinkedList()
        }
        request.paths!!.add(pathParam)
    }

    override fun setJsonBody(exportContext: ExportContext, request: Request, body: Any?, bodyAttr: String?) {
        request.body = body
        request.bodyAttr = bodyAttr
    }

    override fun appendDesc(exportContext: ExportContext, request: Request, desc: String?) {
        request.desc = request.desc.appendln(desc)
    }

    override fun addHeader(exportContext: ExportContext, request: Request, header: Header) {
        if (request.headers == null) {
            request.headers = LinkedList()
        }
        request.headers!!.removeIf { it.name == header.name }
        request.headers!!.add(header)
    }

    override fun addResponse(exportContext: ExportContext, request: Request, response: Response) {
        if (request.response == null) {
            request.response = LinkedList()
        }
        request.response!!.add(response)
    }

    override fun addResponseHeader(exportContext: ExportContext, response: Response, header: Header) {

        if (response.headers == null) {
            response.headers = LinkedList()
        }
        response.headers!!.add(header)
    }

    override fun setResponseBody(exportContext: ExportContext, response: Response, bodyType: String, body: Any?) {
        response.bodyType = bodyType
        response.body = body
    }

    override fun setResponseCode(exportContext: ExportContext, response: Response, code: Int) {
        response.code = code
    }

    override fun appendResponseBodyDesc(exportContext: ExportContext, response: Response, bodyDesc: String?) {
        if (response.bodyDesc.isNullOrBlank()) {
            response.bodyDesc = bodyDesc
        } else {
            response.bodyDesc = response.bodyDesc + "\n" + bodyDesc
        }
    }

    override fun startProcessMethod(methodExportContext: MethodExportContext, request: Request) {
        //NOP
    }

    override fun processCompleted(methodExportContext: MethodExportContext, request: Request) {
        //NOP
    }
}