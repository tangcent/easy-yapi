package com.itangcent.easyapi.exporter.httpclient

import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.cache.DefaultHttpContextCacheHelper
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.core.threading.write
import com.itangcent.easyapi.exporter.ApiExporter
import com.itangcent.easyapi.exporter.formatter.HttpClientFileFormatter
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.OutputConfig

@Service(Service.Level.PROJECT)
class HttpClientExporter(private val project: Project) : ApiExporter {

    override val format: ExportFormat = ExportFormat.HTTP_CLIENT

    companion object {
        fun getInstance(project: Project): HttpClientExporter {
            return project.getService(HttpClientExporter::class.java)
        }
    }

    override suspend fun export(context: ExportContext): ExportResult {
        val host = context.outputConfig.host ?: run {
            val hostCacheHelper = DefaultHttpContextCacheHelper.getInstance(project)
            swing {
                hostCacheHelper.selectHost("Select Host For HTTP Client Export")
            }
        }

        val content =
            HttpClientFileFormatter.formatAll(context.endpointsToExport, host, context.outputConfig.fileName ?: "api")

        return ExportResult.Success(
            count = context.endpointsToExport.size,
            target = "HTTP Client",
            metadata = HttpClientExportMetadata(content = content)
        )
    }

    override suspend fun handleExportResult(
        project: Project,
        result: ExportResult.Success,
        outputConfig: OutputConfig
    ): Boolean {
        val metadata = result.metadata as? HttpClientExportMetadata ?: return false

        val fileName = generateFileName()

        val scratchFile = write() {
            ScratchRootType.getInstance().createScratchFile(
                project,
                fileName,
                Language.ANY,
                metadata.content
            )
        } ?: return false

        swing {
            FileEditorManager.getInstance(project).openFile(scratchFile, true)
        }

        swing {
            showSuccessMessage(project, result, scratchFile.path)
        }
        return true
    }

    private fun generateFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "api_$timestamp.http"
    }

    private fun showSuccessMessage(project: Project, result: ExportResult.Success, target: String) {
        val message = buildString {
            append("Successfully exported ${result.count} endpoints to scratch file")
        }
        com.intellij.openapi.ui.Messages.showInfoMessage(
            project,
            message,
            "Export API"
        )
    }
}

data class HttpClientExportMetadata(val content: String) : com.itangcent.easyapi.exporter.model.ExportMetadata {
    override fun formatDisplay(): String? = null
}
