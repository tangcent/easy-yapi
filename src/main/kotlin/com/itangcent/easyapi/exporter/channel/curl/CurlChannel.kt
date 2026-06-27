package com.itangcent.easyapi.exporter.channel.curl

import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.itangcent.easyapi.cache.http.DefaultHttpContextCacheHelper
import com.itangcent.easyapi.core.threading.background
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.channel.ApiChannel
import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.channel.ChannelOptionsPanel
import com.itangcent.easyapi.exporter.curl.CurlExportMetadata
import com.itangcent.easyapi.exporter.curl.CurlFormatter
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * [ApiChannel] that exports API endpoints as cURL commands.
 *
 * Supports both HTTP and gRPC endpoints. No configuration panel needed.
 *
 * @see ApiChannel
 * @see CurlFormatter
 */
class CurlChannel : ApiChannel, IdeaLog {

    override val id: String = "curl"
    override val displayName: String = "cURL"
    override val supportsGrpc: Boolean = true

    override fun createOptionsPanel(project: Project): ChannelOptionsPanel? = null

    override suspend fun export(context: ExportContext): ExportResult {
        LOG.info("CurlChannel.export: endpoints=${context.endpointsToExport.size}")
        val hostCacheHelper = DefaultHttpContextCacheHelper.getInstance(context.project)
        val host = swing {
            hostCacheHelper.selectHost("Select Host For cURL Export")
        }

        val content = CurlFormatter.formatAll(context.endpointsToExport, host)

        return ExportResult.Success(
            count = context.endpointsToExport.size,
            target = "cURL",
            metadata = CurlExportMetadata(content = content)
        )
    }

    override suspend fun handleResult(
        project: Project,
        result: ExportResult.Success,
        config: ChannelConfig
    ): Boolean {
        val metadata = result.metadata as? CurlExportMetadata ?: return false
        val fileConfig = config as? ChannelConfig.FileConfig

        val targetFile = resolveTargetFile(project, fileConfig, "curl_commands.sh")
            ?: throw CancellationException("User cancelled file selection")

        background {
            targetFile.writeText(metadata.content)
        }

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
}
