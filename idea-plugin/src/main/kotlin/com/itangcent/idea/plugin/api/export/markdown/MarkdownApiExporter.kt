package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.logger.traceError
import com.itangcent.idea.plugin.api.ClassApiExporterHelper
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.settings.helper.MarkdownSettingsHelper
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger

@Singleton
class MarkdownApiExporter {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    private val classExporter: ClassExporter? = null

    @Inject
    private val fileSaveHelper: FileSaveHelper? = null

    @Inject
    private val markdownFormatter: MarkdownFormatter? = null

    @Inject
    private lateinit var messagesHelper: MessagesHelper

    @Inject
    private lateinit var markdownSettingsHelper: MarkdownSettingsHelper

    @Inject
    private lateinit var classApiExporterHelper: ClassApiExporterHelper

    fun export() {
        try {
            val docs = classApiExporterHelper.export()
            if (docs.isEmpty()) {
                logger.info("No api be found to export!")
            } else {
                val apiInfo = markdownFormatter!!.parseRequests(docs)
                fileSaveHelper!!.saveOrCopy(apiInfo, markdownSettingsHelper.outputCharset(), {
                    logger.info("Exported data are copied to clipboard,you can paste to a md file now")
                }, {
                    logger.info("Apis save success: $it")
                }) {
                    logger.info("Apis save failed")
                }
                logger.info("Apis exported completed")
            }
        } catch (e: Exception) {
            logger.traceError("Apis exported failed", e)
        }
    }
}