package com.itangcent.idea.plugin.api.export.translation

import com.google.inject.Inject
import com.itangcent.common.model.*
import com.itangcent.idea.plugin.api.export.core.ExportContext
import com.itangcent.idea.plugin.api.export.core.MethodExportContext
import com.itangcent.idea.plugin.api.export.core.RequestBuilderListener
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.idea.plugin.settings.helper.AISettingsHelper
import com.itangcent.intellij.logger.Logger

/**
 * RequestBuilderListener implementation that translates API documentation
 * during the export process
 */
@ConditionOnSetting("aiTranslationEnabled")
class TranslationRequestBuilderListener : RequestBuilderListener {

    @Inject
    private lateinit var apiTranslationHelper: APITranslationHelper

    @Inject
    private lateinit var aiSettingsHelper: AISettingsHelper

    @Inject
    private lateinit var logger: Logger

    override fun setName(exportContext: ExportContext, request: Request, name: String) {
        //NOP
    }

    override fun setMethod(exportContext: ExportContext, request: Request, method: String) {
        //NOP
    }

    override fun setPath(exportContext: ExportContext, request: Request, path: URL) {
        //NOP
    }

    override fun setModelAsBody(exportContext: ExportContext, request: Request, model: Any) {
        //NOP
    }

    override fun addModelAsParam(exportContext: ExportContext, request: Request, model: Any) {
        //NOP
    }

    override fun addModelAsFormParam(exportContext: ExportContext, request: Request, model: Any) {
        //NOP
    }

    override fun addFormParam(exportContext: ExportContext, request: Request, formParam: FormParam) {
        //NOP
    }

    override fun addParam(exportContext: ExportContext, request: Request, param: Param) {
        //NOP
    }

    override fun removeParam(exportContext: ExportContext, request: Request, param: Param) {
        //NOP
    }

    override fun addPathParam(exportContext: ExportContext, request: Request, pathParam: PathParam) {
        //NOP
    }

    override fun setJsonBody(exportContext: ExportContext, request: Request, body: Any?, bodyAttr: String?) {
        //NOP
    }

    override fun appendDesc(exportContext: ExportContext, request: Request, desc: String?) {
        //NOP
    }

    override fun addHeader(exportContext: ExportContext, request: Request, header: Header) {
        //NOP
    }

    override fun addResponse(exportContext: ExportContext, request: Request, response: Response) {
        //NOP
    }

    override fun addResponseHeader(exportContext: ExportContext, response: Response, header: Header) {
        //NOP
    }

    override fun setResponseBody(exportContext: ExportContext, response: Response, bodyType: String, body: Any?) {
        //NOP
    }

    override fun setResponseCode(exportContext: ExportContext, response: Response, code: Int) {
        //NOP
    }

    override fun appendResponseBodyDesc(exportContext: ExportContext, response: Response, bodyDesc: String?) {
        //NOP
    }

    override fun startProcessMethod(methodExportContext: MethodExportContext, request: Request) {
        //NOP
    }

    /**
     * Process the completed request by translating its documentation if translation is enabled
     */
    override fun processCompleted(methodExportContext: MethodExportContext, request: Request) {
        try {
            val targetLanguage = aiSettingsHelper.translationTargetLanguageName
            if (targetLanguage.isNullOrBlank()) {
                return
            }

            logger.info("Translating API documentation to $targetLanguage: ${request.name}")

            apiTranslationHelper.translateRequest(request)
        } catch (e: Exception) {
            logger.error("Failed to translate request: ${e.message}")
        }
    }
} 