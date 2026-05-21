package com.itangcent.easyapi.exporter.channel.markdown

import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.itangcent.easyapi.core.threading.background
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.channel.ApiChannel
import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.channel.ChannelOptionsPanel
import com.itangcent.easyapi.exporter.markdown.DefaultMarkdownFormatter
import com.itangcent.easyapi.exporter.markdown.MarkdownFormatter
import com.itangcent.easyapi.exporter.markdown.MarkdownExportMetadata
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.CancellationException
import java.awt.BorderLayout
import java.awt.CardLayout
import java.io.File
import javax.swing.*

/**
 * [ApiChannel] that exports API endpoints as Markdown documentation.
 *
 * Supports both HTTP and gRPC endpoints. Exposes a top-level IDE action
 * for quick access.
 *
 * @see ApiChannel
 * @see DefaultMarkdownFormatter
 */
class MarkdownChannel : ApiChannel, IdeaLog {

    override val id: String = "markdown"
    override val displayName: String = "Markdown"
    override val supportsGrpc: Boolean = true
    override val exposeAsAction: Boolean = true
    override val actionText: String = "Export to Markdown"

    private val formatter: MarkdownFormatter = DefaultMarkdownFormatter()

    override fun createOptionsPanel(project: Project): ChannelOptionsPanel {
        return MarkdownOptionsPanel(project)
    }

    override suspend fun export(context: ExportContext): ExportResult {
        val content = formatter.format(context.endpointsToExport, "API Documentation")
        return ExportResult.Success(
            count = context.endpointsToExport.size,
            target = "Markdown",
            metadata = MarkdownExportMetadata(content = content)
        )
    }

    override suspend fun handleResult(
        project: Project,
        result: ExportResult.Success,
        config: ChannelConfig
    ): Boolean {
        val metadata = result.metadata as? MarkdownExportMetadata ?: return false
        val fileConfig = config as? ChannelConfig.FileConfig

        val targetFile = resolveTargetFile(project, fileConfig, "api_documentation.md")
            ?: throw CancellationException("User cancelled file selection")

        background {
            targetFile.writeText(metadata.content)
        }
        LOG.info("Markdown exported to ${targetFile.absolutePath}")

        swing {
            com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                "Successfully exported ${result.count} endpoints to ${targetFile.absolutePath}",
                "Export API"
            )
        }
        return true
    }

    private suspend fun resolveTargetFile(
        project: Project,
        fileConfig: ChannelConfig.FileConfig?,
        defaultFileName: String
    ): File? {
        val outputDir = fileConfig?.outputDir
        val fileName = fileConfig?.fileName
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
}

private class MarkdownOptionsPanel(private val project: Project) : ChannelOptionsPanel {

    private val outputDirField = com.intellij.openapi.ui.TextFieldWithBrowseButton().apply {
        text = project.basePath ?: ""
        addBrowseFolderListener(
            project,
            com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Output Directory")
                .withDescription("Choose the directory to export API files to")
        )
    }

    private val fileNameField = com.intellij.ui.components.JBTextField().apply {
        text = "api_export"
        columns = 30
    }

    override val component: JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(JPanel(BorderLayout()).apply {
            add(JLabel("Output Directory:"), BorderLayout.WEST)
            add(outputDirField, BorderLayout.CENTER)
        })
        add(Box.createVerticalStrut(5))
        add(JPanel(BorderLayout()).apply {
            add(JLabel("File Name (without extension):"), BorderLayout.WEST)
            add(fileNameField, BorderLayout.CENTER)
        })
    }

    override fun buildConfig(): ChannelConfig.FileConfig {
        return ChannelConfig.FileConfig(
            outputDir = outputDirField.text.takeIf { it.isNotBlank() },
            fileName = fileNameField.text.takeIf { it.isNotBlank() }
        )
    }
}
