package com.itangcent.easyapi.exporter.curl

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.itangcent.easyapi.cache.DefaultHttpContextCacheHelper
import com.itangcent.easyapi.core.threading.background
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.ApiExporter
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.OutputConfig
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Exporter for converting API endpoints to cURL commands.
 * 
 * This exporter generates executable shell scripts containing cURL commands
 * for each API endpoint. It supports:
 * - Host selection from cached history
 * - File save dialog for output
 * - Proper shell escaping
 * 
 * The generated script can be used for testing APIs from the command line.
 */
@Service(Service.Level.PROJECT)
class CurlExporter(private val project: Project) : ApiExporter {

    /** The export format this exporter handles */
    override val format: ExportFormat = ExportFormat.CURL

    /**
     * Returns the singleton instance for the given project.
     */
    companion object {
        fun getInstance(project: Project): CurlExporter {
            return project.getService(CurlExporter::class.java)
        }
    }

    /**
     * Exports API endpoints to cURL commands.
     * Prompts for host selection if not provided in the output config.
     * 
     * @param context The export context containing endpoints and configuration
     * @return Success result with cURL content, or error result
     */
    override suspend fun export(context: ExportContext): ExportResult {
        val host = context.outputConfig.host ?: run {
            val hostCacheHelper = DefaultHttpContextCacheHelper.getInstance(project)
            swing {
                hostCacheHelper.selectHost("Select Host For cURL Export")
            }
        }

        val content = CurlFormatter.formatAll(context.endpointsToExport, host)

        return ExportResult.Success(
            count = context.endpointsToExport.size,
            target = "cURL",
            metadata = CurlExportMetadata(content = content)
        )
    }

    /**
     * Handles the export result by saving to a file.
     * Shows a file save dialog and writes the cURL commands.
     * 
     * @param project The IntelliJ project
     * @param result The successful export result
     * @return true if the file was saved successfully, false otherwise
     */
    override suspend fun handleExportResult(
        project: Project,
        result: ExportResult.Success,
        outputConfig: OutputConfig
    ): Boolean {
        val metadata = result.metadata as? CurlExportMetadata ?: return false

        val targetFile = resolveTargetFile(project, outputConfig, "curl_commands.sh")
            ?: throw CancellationException("User cancelled file selection")

        background {
            targetFile.writeText(metadata.content)
        }

        swing {
            showSuccessMessage(project, result, targetFile.absolutePath)
        }
        return true
    }

    private suspend fun resolveTargetFile(
        project: Project,
        outputConfig: OutputConfig,
        defaultFileName: String
    ): File? {
        val outputDir = outputConfig.outputDir
        val fileName = outputConfig.fileName
        if (!outputDir.isNullOrBlank()) {
            val dir = File(outputDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val name = if (!fileName.isNullOrBlank()) "$fileName.sh" else defaultFileName
            return File(dir, name)
        }
        return selectTargetFile(project, defaultFileName)
    }

    private suspend fun selectTargetFile(project: Project, defaultFileName: String): File? {
        return swing {
            val descriptor = FileSaverDescriptor(
                "Save cURL Commands",
                "Choose where to save the cURL commands"
            )
            val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)

            val wrapper: VirtualFileWrapper? = saver.save(null as VirtualFile?, defaultFileName)
            wrapper?.file
        }
    }

    /**
     * Shows a success message after export completion.
     * 
     * @param project The IntelliJ project
     * @param result The successful export result
     * @param target The path where the file was saved
     */
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

data class CurlExportMetadata(val content: String) : com.itangcent.easyapi.exporter.model.ExportMetadata {
    override fun formatDisplay(): String? = null
}
