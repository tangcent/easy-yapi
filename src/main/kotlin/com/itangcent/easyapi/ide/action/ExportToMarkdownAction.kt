package com.itangcent.easyapi.ide.action

import com.itangcent.easyapi.exporter.model.ExportFormat

/**
 * Action to export APIs to Markdown format.
 *
 * Extends [BaseExportAction] with Markdown-specific format configuration.
 *
 * @see BaseExportAction for the export implementation
 * @see ExportFormat.MARKDOWN for the target format
 */
class ExportToMarkdownAction : BaseExportAction() {

    override val exportFormat: ExportFormat = ExportFormat.MARKDOWN
    override val actionName: String = "Export to Markdown"
}
