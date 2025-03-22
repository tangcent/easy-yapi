package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.project.Project
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Doc
import com.itangcent.idea.plugin.api.ClassApiExporterHelper
import com.itangcent.idea.plugin.settings.helper.MarkdownSettingsHelper
import com.itangcent.idea.plugin.utils.NotificationUtils
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.intellij.logger.Logger

@Singleton
class MarkdownApiExporter {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private val fileSaveHelper: FileSaveHelper? = null

    @Inject
    private val markdownFormatter: MarkdownFormatter? = null

    @Inject
    private lateinit var markdownSettingsHelper: MarkdownSettingsHelper

    @Inject
    private lateinit var classApiExporterHelper: ClassApiExporterHelper

    @Inject
    private lateinit var project: Project

    fun export() {
        try {
            val docs = classApiExporterHelper.export()
            if (docs.isEmpty()) {
                NotificationUtils.notifyInfo(project, "No API found to export")
            } else {
                export(docs)
            }
        } catch (e: Exception) {
            logger.traceError("Apis exported failed", e)
            NotificationUtils.notifyError(project, "Failed to export APIs: ${e.message}")
        }
    }

    fun export(docs: List<Doc>) {
        if (docs.isEmpty()) {
            NotificationUtils.notifyInfo(project, "No API found to export")
            return
        }
        val apiInfo = markdownFormatter!!.parseDocs(docs)
        fileSaveHelper!!.saveOrCopy(apiInfo, markdownSettingsHelper.outputCharset(), {
            NotificationUtils.notifyInfo(project, "API documentation copied to clipboard")
        }, {
            NotificationUtils.notifyInfo(project, "APIs exported successfully to: $it")
        }) {
            NotificationUtils.notifyError(project, "Failed to save API documentation")
        }
        logger.info("Apis exported completed")
    }
}