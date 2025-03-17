package com.itangcent.idea.plugin.api.export.core

import com.google.inject.ImplementedBy
import com.itangcent.common.model.*

@ImplementedBy(CompositeRequestBuilderListener::class)
interface RequestBuilderListener {

    fun setName(
        exportContext: ExportContext,
        request: Request,
        name: String,
    )

    fun setMethod(
        exportContext: ExportContext,
        request: Request,
        method: String,
    )

    fun setPath(
        exportContext: ExportContext,
        request: Request,
        path: URL,
    )

    /**
     * addAsJsonBody if content-type is json
     * otherwise addAsForm
     */
    fun setModelAsBody(
        exportContext: ExportContext,
        request: Request,
        model: Any,
    )

    fun addModelAsParam(
        exportContext: ExportContext,
        request: Request,
        model: Any,
    )

    fun addModelAsFormParam(
        exportContext: ExportContext,
        request: Request,
        model: Any,
    )

    fun addFormParam(
        exportContext: ExportContext,
        request: Request,
        formParam: FormParam,
    )

    fun addParam(
        exportContext: ExportContext,
        request: Request,
        param: Param,
    )

    fun removeParam(
        exportContext: ExportContext,
        request: Request,
        param: Param,
    )

    fun addPathParam(
        exportContext: ExportContext,
        request: Request,
        pathParam: PathParam,
    )

    fun setJsonBody(
        exportContext: ExportContext,
        request: Request,
        body: Any?,
        bodyAttr: String?,
    )

    fun appendDesc(
        exportContext: ExportContext,
        request: Request,
        desc: String?,
    )

    fun addHeader(
        exportContext: ExportContext,
        request: Request,
        header: Header,
    )

    //region response
    fun addResponse(
        exportContext: ExportContext,
        request: Request,
        response: Response,
    )

    fun addResponseHeader(
        exportContext: ExportContext,
        response: Response,
        header: Header,
    )

    fun setResponseBody(
        exportContext: ExportContext,
        response: Response,
        bodyType: String,
        body: Any?,
    )

    fun setResponseCode(
        exportContext: ExportContext,
        response: Response,
        code: Int,
    )

    fun appendResponseBodyDesc(
        exportContext: ExportContext,
        response: Response,
        bodyDesc: String?,
    )

    //endregion

    fun startProcessMethod(methodExportContext: MethodExportContext, request: Request)

    fun processCompleted(methodExportContext: MethodExportContext, request: Request)
}

//region utils------------------------------------------------------------------

fun RequestBuilderListener.addParam(
    exportContext: ExportContext, request: Request,
    paramName: String, value: Any?, attr: String?,
) {
    addParam(
        exportContext, request,
        paramName, value, false, attr
    )
}

fun RequestBuilderListener.addParam(
    exportContext: ExportContext, request: Request,
    paramName: String, value: Any?, required: Boolean, desc: String?,
): Param {
    val param = Param()
    param.name = paramName
    param.value = value
    param.required = required
    param.desc = desc
    this.addParam(
        exportContext, request,
        param
    )
    return param
}

fun RequestBuilderListener.addFormParam(
    exportContext: ExportContext, request: Request,
    paramName: String, defaultVal: String?, desc: String?,
) {
    addFormParam(
        exportContext, request,
        paramName, defaultVal, false, desc
    )
}

fun RequestBuilderListener.addFormParam(
    exportContext: ExportContext, request: Request,
    paramName: String, value: String?, required: Boolean, desc: String?,
): FormParam {
    val param = FormParam()
    param.name = paramName
    param.value = value
    param.required = required
    param.desc = desc
    param.type = "text"
    this.addFormParam(
        exportContext, request,
        param
    )
    return param
}

fun RequestBuilderListener.addFormFileParam(
    exportContext: ExportContext, request: Request,
    paramName: String, required: Boolean, desc: String?,
): FormParam {
    val param = FormParam()
    param.name = paramName
    param.required = required
    param.desc = desc
    param.type = "file"
    this.addFormParam(
        exportContext, request,
        param
    )
    return param
}

fun RequestBuilderListener.addHeader(
    exportContext: ExportContext, request: Request,
    name: String, value: String?,
): Header {
    val header = Header()
    header.name = name
    header.value = value
    header.required = true
    addHeader(
        exportContext, request,
        header
    )
    return header
}

fun RequestBuilderListener.addHeaderIfMissed(
    exportContext: ExportContext, request: Request,
    name: String, value: String?,
): Boolean {
    if (request.header(name) != null) {
        return false
    }
    addHeader(
        exportContext, request,
        name, value
    )
    return true
}

fun RequestBuilderListener.addPathParam(
    exportContext: ExportContext, request: Request,
    name: String, desc: String?,
) {
    val pathParam = PathParam()
    pathParam.name = name
    pathParam.desc = desc
    this.addPathParam(
        exportContext, request,
        pathParam
    )
}

fun RequestBuilderListener.addPathParam(
    exportContext: ExportContext, request: Request,
    name: String, value: String?, desc: String?,
) {
    val pathParam = PathParam()
    pathParam.name = name
    pathParam.value = value
    pathParam.desc = desc
    this.addPathParam(
        exportContext, request,
        pathParam
    )
}

fun RequestBuilderListener.addResponseHeader(
    exportContext: ExportContext,
    response: Response,
    name: String,
    value: String?,
) {
    val header = Header()
    header.name = name
    header.value = value
    addResponseHeader(exportContext, response, header)
}

fun RequestBuilderListener.setMethodIfMissed(
    exportContext: ExportContext, request: Request,
    method: String,
) {
    if (request.hasMethod()) {
        return
    }
    this.setMethod(
        exportContext, request,
        method
    )
}

fun RequestBuilderListener.setContentType(
    exportContext: ExportContext, request: Request,
    contentType: String,
) {
    this.addHeader(
        exportContext, request,
        "Content-Type", contentType
    )
}

//endregion utils------------------------------------------------------------------