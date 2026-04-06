package com.itangcent.easyapi.exporter.httpclient

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.itangcent.easyapi.cache.DefaultHttpContextCacheHelper
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.ApiExporter
import com.itangcent.easyapi.exporter.formatter.HttpClientFileFormatter
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

        val content = HttpClientFileFormatter.formatAll(context.endpointsToExport, host, context.outputConfig.fileName ?: "api")

        return ExportResult.Success(
            count = context.endpointsToExport.size,
            target = "HTTP Client",
            metadata = HttpClientExportMetadata(content = content)
        )
    }

    override suspend fun handleExportResult(project: Project, result: ExportResult.Success): Boolean {
        val metadata = result.metadata as? HttpClientExportMetadata ?: return false

        val targetFile = selectTargetFile(project) ?: return false

        withContext(Dispatchers.IO) {
            targetFile.writeText(metadata.content)
        }

        showSuccessMessage(project, result, targetFile.absolutePath)
        return true
    }

    private suspend fun selectTargetFile(project: Project): File? {
        return swing {
            val descriptor = FileSaverDescriptor(
                "Save HTTP Client File",
                "Choose where to save the HTTP client file"
            )
            val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)

            val wrapper: VirtualFileWrapper? = saver.save(null as VirtualFile?, "api.http")
            wrapper?.file
        }
    }

    private fun showSuccessMessage(project: Project, result: ExportResult.Success, target: String) {
        val message = buildString {
            append("Successfully exported ${result.count} endpoints to $target")
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
