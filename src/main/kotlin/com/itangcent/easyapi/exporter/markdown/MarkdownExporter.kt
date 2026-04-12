package com.itangcent.easyapi.exporter.markdown

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.itangcent.easyapi.core.threading.background
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.ApiExporter
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.OutputConfig
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Exporter for converting API endpoints to Markdown documentation.
 * 
 * This exporter generates human-readable Markdown files suitable for
 * documentation purposes. It uses the DefaultMarkdownFormatter to create
 * structured documentation with tables for parameters and request/response bodies.
 */
@Service(Service.Level.PROJECT)
class MarkdownExporter(private val project: Project) : ApiExporter, IdeaLog {

    /** The export format this exporter handles */
    override val format: ExportFormat = ExportFormat.MARKDOWN

    /** The formatter used to generate Markdown content */
    private val formatter: MarkdownFormatter = DefaultMarkdownFormatter()

    /**
     * Returns the singleton instance for the given project.
     */
    companion object {
        fun getInstance(project: Project): MarkdownExporter {
            return project.getService(MarkdownExporter::class.java)
        }
    }

    /**
     * Exports API endpoints to Markdown documentation.
     * 
     * @param context The export context containing endpoints and configuration
     * @return Success result with Markdown content, or error result
     */
    override suspend fun export(context: ExportContext): ExportResult {
        val content = formatter.format(context.endpointsToExport, "API Documentation")

        return ExportResult.Success(
            count = context.endpointsToExport.size,
            target = "Markdown",
            metadata = MarkdownExportMetadata(content = content)
        )
    }

    /**
     * Handles the export result by saving to a file.
     * Shows a file save dialog and writes the Markdown content.
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
        val metadata = result.metadata as? MarkdownExportMetadata ?: return false

        val targetFile = resolveTargetFile(project, outputConfig, "api_documentation.md")
            ?: throw CancellationException("User cancelled file selection")

        background {
            targetFile.writeText(metadata.content)
        }
        LOG.info("Markdown exported to ${targetFile.absolutePath}")

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
            val name = if (!fileName.isNullOrBlank()) "$fileName.md" else defaultFileName
            return File(dir, name)
        }
        return selectTargetFile(project, defaultFileName)
    }

    private suspend fun selectTargetFile(project: Project, defaultFileName: String): File? {
        return swing {
            val descriptor = FileSaverDescriptor(
                "Save Markdown Documentation",
                "Choose where to save the Markdown file"
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

/**
 * Metadata for Markdown export results.
 * 
 * @property content The generated Markdown content
 */
data class MarkdownExportMetadata(val content: String) : com.itangcent.easyapi.exporter.model.ExportMetadata {
    override fun formatDisplay(): String? = null
}
