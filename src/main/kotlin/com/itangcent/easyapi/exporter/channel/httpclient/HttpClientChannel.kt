package com.itangcent.easyapi.exporter.channel.httpclient

import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.cache.http.DefaultHttpContextCacheHelper
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.core.threading.write
import com.itangcent.easyapi.exporter.channel.ApiChannel
import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.channel.ChannelOptionsPanel
import com.itangcent.easyapi.exporter.formatter.HttpClientFileFormatter
import com.itangcent.easyapi.exporter.httpclient.HttpClientExportMetadata
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.logging.IdeaLog

/**
 * [ApiChannel] that exports API endpoints as IntelliJ HTTP Client files.
 *
 * Creates `.http` scratch files that can be executed directly in the IDE.
 * Supports both HTTP and gRPC endpoints. No configuration panel needed.
 *
 * @see ApiChannel
 * @see HttpClientFileFormatter
 */
class HttpClientChannel : ApiChannel, IdeaLog {

    override val id: String = "http-client"
    override val displayName: String = "HTTP Client"
    override val supportsGrpc: Boolean = true

    override fun createOptionsPanel(project: Project): ChannelOptionsPanel? = null

    override suspend fun export(context: ExportContext): ExportResult {
        LOG.info("HttpClientChannel.export: endpoints=${context.endpointsToExport.size}")
        val hostCacheHelper = DefaultHttpContextCacheHelper.getInstance(context.project)
        val host = swing {
            hostCacheHelper.selectHost("Select Host For HTTP Client Export")
        }

        val content = HttpClientFileFormatter.formatAll(
            context.endpointsToExport,
            host,
            "api"
        )

        return ExportResult.Success(
            count = context.endpointsToExport.size,
            target = "HTTP Client",
            metadata = HttpClientExportMetadata(content = content)
        )
    }

    override suspend fun handleResult(
        project: Project,
        result: ExportResult.Success,
        config: ChannelConfig
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
            com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                "Successfully exported ${result.count} endpoints to scratch file",
                "Export API"
            )
        }
        return true
    }

    private fun generateFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "api_$timestamp.http"
    }
}
