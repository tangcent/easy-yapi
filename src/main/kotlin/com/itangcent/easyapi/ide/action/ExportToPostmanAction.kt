package com.itangcent.easyapi.ide.action

import com.itangcent.easyapi.exporter.model.ExportFormat

/**
 * Action to export APIs to Postman.
 *
 * Extends [BaseExportAction] with Postman-specific format configuration.
 *
 * @see BaseExportAction for the export implementation
 * @see ExportFormat.POSTMAN for the target format
 */
class ExportToPostmanAction : BaseExportAction() {

    override val exportFormat: ExportFormat = ExportFormat.POSTMAN
    override val actionName: String = "Export to Postman"
}
