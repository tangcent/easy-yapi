package com.itangcent.common.exporter

import com.itangcent.common.constant.Attrs
import com.itangcent.common.model.*

abstract class AbstractParseHandle : ParseHandle {
    override fun setName(request: Request, name: String) {
        request.name = name
    }

    override fun setMethod(request: Request, method: String) {
        request.method = method
    }

    override fun setPath(request: Request, path: String) {
        request.path = path
    }

    override fun setModelAsBody(request: Request, model: Any) {
        request.body = model
    }

    override fun addModelAsParam(request: Request, model: Any) {
        if (model is Map<*, *>) {
            val comment = model[Attrs.COMMENT_ATTR] as Map<*, *>?
            model.forEach { k, v ->
                addFormParam(request, k.toString(), v.toString(),
                        comment?.get(k)?.toString())
            }
        }
    }

    override fun addFormParam(request: Request, formParam: FormParam) {
        if (request.formParams == null) {
            request.formParams = ArrayList()
        }
        request.formParams!!.add(formParam)
    }

    override fun addParam(request: Request, param: Param) {
        if (request.querys == null) {
            request.querys = ArrayList()
        }
        request.querys!!.add(param)
    }

    override fun addPathParam(request: Request, pathParam: PathParam) {
        if (request.paths == null) {
            request.paths = ArrayList()
        }
        request.paths!!.add(pathParam)
    }

    override fun setJsonBody(request: Request, body: Any?, bodyAttr: String?) {
        request.body = body
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
            request.headers = ArrayList()
        }
        request.headers!!.add(header)
    }

    override fun addResponse(request: Request, response: Response) {
        if (request.response == null) {
            request.response = ArrayList()
        }
        request.response!!.add(response)
    }

    override fun addResponseHeader(response: Response, header: Header) {

        if (response.headers == null) {
            response.headers = ArrayList()
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
}