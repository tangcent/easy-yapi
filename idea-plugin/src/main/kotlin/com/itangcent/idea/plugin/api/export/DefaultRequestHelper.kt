package com.itangcent.idea.plugin.api.export

import com.google.inject.Singleton
import com.itangcent.common.constant.Attrs
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.model.*
import java.util.*

@Singleton
open class DefaultRequestHelper : RequestHelper {
    override fun setName(request: Request, name: String) {
        request.name = name
    }

    override fun setMethod(request: Request, method: String) {
        request.method = method
    }

    override fun setPath(request: Request, path: URL) {
        request.path = path
    }

    override fun setModelAsBody(request: Request, model: Any) {
        request.body = model
    }

    override fun addModelAsParam(request: Request, model: Any) {
        if (model is Map<*, *>) {
            val comment = model[Attrs.COMMENT_ATTR] as Map<*, *>?
            model.forEach { (k, v) ->
                addFormParam(
                        request, k.toString(), v.toString(),
                        KVUtils.getUltimateComment(comment, k)
                )
            }
        }
    }

    override fun addFormParam(request: Request, formParam: FormParam) {
        if (request.formParams == null) {
            request.formParams = LinkedList()
        }
        request.formParams!!.add(formParam)
    }

    override fun addParam(request: Request, param: Param) {
        if (request.querys == null) {
            request.querys = LinkedList()
        }
        request.querys!!.add(param)
    }

    override fun addPathParam(request: Request, pathParam: PathParam) {
        if (request.paths == null) {
            request.paths = LinkedList()
        }
        request.paths!!.add(pathParam)
    }

    override fun setJsonBody(request: Request, body: Any?, bodyAttr: String?) {
        request.body = body
        request.bodyAttr = bodyAttr
    }

    override fun appendDesc(request: Request, desc: String?) {
        if (request.desc == null) {
            request.desc = desc
        } else {
            request.desc = "${request.desc}$desc"
        }
    }

    override fun addHeader(request: Request, header: Header) {
        if (request.headers == null) {
            request.headers = LinkedList()
        }
        request.headers!!.removeIf { it.name == header.name }
        request.headers!!.add(header)
    }

    override fun addResponse(request: Request, response: Response) {
        if (request.response == null) {
            request.response = LinkedList()
        }
        request.response!!.add(response)
    }

    override fun addResponseHeader(response: Response, header: Header) {

        if (response.headers == null) {
            response.headers = LinkedList()
        }
        response.headers!!.add(header)
    }

    override fun setResponseBody(response: Response, bodyType: String, body: Any?) {
        response.bodyType = bodyType
        response.body = body
    }

    override fun setResponseCode(response: Response, code: Int) {
        response.code = code
    }

    override fun appendResponseBodyDesc(response: Response, bodyDesc: String?) {
        if (response.bodyDesc.isNullOrBlank()) {
            response.bodyDesc = bodyDesc
        } else {
            response.bodyDesc = response.bodyDesc + "\n" + bodyDesc
        }
    }
}