package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Singleton
import com.itangcent.common.model.*
import com.itangcent.spi.SpiCompositeLoader

@Singleton
class CompositeRequestBuilderListener :
    RequestBuilderListener {

    private val delegate: RequestBuilderListener by lazy {
        SpiCompositeLoader.loadComposite()
    }

    override fun setName(exportContext: ExportContext, request: Request, name: String) {
        delegate.setName(exportContext, request, name)
    }

    override fun setMethod(exportContext: ExportContext, request: Request, method: String) {
        delegate.setMethod(exportContext, request, method)
    }

    override fun setPath(exportContext: ExportContext, request: Request, path: URL) {
        delegate.setPath(exportContext, request, path)
    }

    override fun setModelAsBody(exportContext: ExportContext, request: Request, model: Any) {
        delegate.setModelAsBody(exportContext, request, model)
    }

    override fun addModelAsParam(exportContext: ExportContext, request: Request, model: Any) {
        delegate.addModelAsParam(exportContext, request, model)
    }

    override fun addFormParam(exportContext: ExportContext, request: Request, formParam: FormParam) {
        delegate.addFormParam(exportContext, request, formParam)
    }

    override fun addParam(exportContext: ExportContext, request: Request, param: Param) {
        delegate.addParam(exportContext, request, param)
    }

    override fun removeParam(exportContext: ExportContext, request: Request, param: Param) {
        delegate.removeParam(exportContext, request, param)
    }

    override fun addPathParam(exportContext: ExportContext, request: Request, pathParam: PathParam) {
        delegate.addPathParam(exportContext, request, pathParam)
    }

    override fun setJsonBody(exportContext: ExportContext, request: Request, body: Any?, bodyAttr: String?) {
        delegate.setJsonBody(exportContext, request, body, bodyAttr)
    }

    override fun appendDesc(exportContext: ExportContext, request: Request, desc: String?) {
        delegate.appendDesc(exportContext, request, desc)
    }

    override fun addHeader(exportContext: ExportContext, request: Request, header: Header) {
        delegate.addHeader(exportContext, request, header)
    }

    override fun addResponse(exportContext: ExportContext, request: Request, response: Response) {
        delegate.addResponse(exportContext, request, response)
    }

    override fun addResponseHeader(exportContext: ExportContext, response: Response, header: Header) {
        delegate.addResponseHeader(exportContext, response, header)
    }

    override fun setResponseBody(exportContext: ExportContext, response: Response, bodyType: String, body: Any?) {
        delegate.setResponseBody(exportContext, response, bodyType, body)
    }

    override fun setResponseCode(exportContext: ExportContext, response: Response, code: Int) {
        delegate.setResponseCode(exportContext, response, code)
    }

    override fun appendResponseBodyDesc(exportContext: ExportContext, response: Response, bodyDesc: String?) {
        delegate.appendResponseBodyDesc(exportContext, response, bodyDesc)
    }

    override fun startProcessMethod(methodExportContext: MethodExportContext, request: Request) {
        delegate.startProcessMethod(methodExportContext, request)
    }

    override fun processCompleted(methodExportContext: MethodExportContext, request: Request) {
        delegate.processCompleted(methodExportContext, request)
    }
}