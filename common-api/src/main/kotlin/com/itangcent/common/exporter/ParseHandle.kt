package com.itangcent.common.exporter

import com.itangcent.common.model.*

interface ParseHandle {

    fun setName(request: Request, name: String)

    fun setMethod(request: Request, method: String)

    fun setPath(request: Request, path: String)

    /**
     * addAsJsonBody if content-type is json
     * otherwise addAsForm
     */
    fun setModelAsBody(request: Request, model: Any)

    fun addModelAsParam(request: Request, model: Any)

    fun addFormParam(request: Request, formParam: FormParam)

    fun addParam(request: Request, param: Param)

    fun addPathParam(request: Request, pathParam: PathParam)

    fun setJsonBody(request: Request, body: Any?, bodyAttr: String?)

    fun appendDesc(request: Request, desc: String?)

    fun addHeader(request: Request, header: Header)

    //region response
    fun addResponse(request: Request, response: Response)

    fun addResponseHeader(response: Response, header: Header)

    fun setResponseBody(response: Response, bodyType: String, body: Any?)

    fun setResponseCode(response: Response, code: Int)
    //endregion

    fun linkToClass(linkClass: Any): String?

    fun linkToMethod(linkMethod: Any): String?

    fun linkToProperty(linkField: Any): String?
}

//region utils------------------------------------------------------------------
fun ParseHandle.addParam(request: Request, paramName: String, defaultVal: String?, attr: String?) {
    addParam(request, paramName, defaultVal, false, attr)
}

fun ParseHandle.addParam(request: Request, paramName: String, defaultVal: String?, required: Boolean, desc: String?) {
    val param = Param()
    param.name = paramName
    param.value = defaultVal
    param.required = required
    param.desc = desc
    this.addParam(request, param)
}

fun ParseHandle.addFormParam(request: Request, paramName: String, defaultVal: String?, attr: String?) {
    addFormParam(request, paramName, defaultVal, false, attr)
}

fun ParseHandle.addFormParam(request: Request, paramName: String, defaultVal: String?, required: Boolean, desc: String?) {
    val param = FormParam()
    param.name = paramName
    param.value = defaultVal
    param.required = required
    param.desc = desc
    param.type = "text"
    this.addFormParam(request, param)
}

fun ParseHandle.addFormFileParam(request: Request, paramName: String, required: Boolean, desc: String?) {
    val param = FormParam()
    param.name = paramName
    param.required = required
    param.desc = desc
    param.type = "file"
    this.addFormParam(request, param)
}

fun ParseHandle.addHeader(request: Request, name: String, value: String) {
    val header = Header()
    header.name = name
    header.value = value
    header.required = true
    addHeader(request, header)
}

fun ParseHandle.addPathParam(request: Request, name: String, desc: String) {
    val pathParam = PathParam()
    pathParam.name = name
    pathParam.desc = desc
    this.addPathParam(request, pathParam)
}

fun ParseHandle.addResponseHeader(response: Response, name: String, value: String) {
    val header = Header()
    header.name = name
    header.value = value
    addResponseHeader(response, header)
}
//endregion utils------------------------------------------------------------------