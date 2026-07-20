package com.itangcent.easyapi.channel.curl

import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.itangcent.easyapi.core.cache.http.DefaultHttpContextCacheHelper
import com.itangcent.easyapi.core.internal.threading.background
import com.itangcent.easyapi.core.internal.threading.swing
import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.channel.spi.ChannelOptionsPanel
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.settings.ui.SettingsPanel
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlin.reflect.KClass

/**
 * [Channel] that exports API endpoints as cURL commands.
 *
 * Supports both HTTP and gRPC endpoints. Variable rendering (`{{var}}` / `${var}`)
 * is driven by [CurlSettings.renderMode] and delegated to [CurlExportResolver]; the
 * per-export formatting flags come from [CurlConfig.options] (collected by
 * [CurlOptionsPanel] in the export dialog). Persistent formatting defaults live in
 * [CurlSettings] and are edited via [CurlSettingsPanel] in the EasyApi settings.
 *
 * @see Channel
 * @see CurlFormatter
 * @see CurlExportResolver
 */
class CurlChannel : Channel, IdeaLog {

    override val id: String = "curl"
    override val displayName: String = "cURL"
    override val supportsGrpc: Boolean = true

    override val settingsType: KClass<out Settings> = CurlSettings::class
    override val settingsTabOrder: Int = 110

    override fun createOptionsPanel(project: Project): ChannelOptionsPanel = CurlOptionsPanel(project)

    override fun createSettingsPanel(project: Project): SettingsPanel<*>? = CurlSettingsPanel(project)

    override suspend fun export(context: ExportContext): ExportResult {
        LOG.info("CurlChannel.export: endpoints=${context.endpointsToExport.size}")
        val project = context.project
        val hostCacheHelper = DefaultHttpContextCacheHelper.getInstance(project)
        val host = swing {
            hostCacheHelper.selectHost("Select Host For cURL Export")
        }

        val curlConfig = context.channelConfig as? CurlConfig
        val options = curlConfig?.options ?: CurlFormatOptions()
        // runPreScripts: per-export override (null → use persistent CurlSettings default).
        val runPreScripts = curlConfig?.runPreScripts ?: project.settings<CurlSettings>().runPreScripts

        // CurlExportResolver is a project service — it reads renderMode from
        // CurlSettings internally. resetBatchCache() ensures a fresh prompt per export.
        val resolver = CurlExportResolver.getInstance(project)
        resolver.resetBatchCache()
        val resolved = resolver.resolveAll(context.endpointsToExport, host)
            ?: throw CancellationException("User cancelled environment selection")

        // Run folder+class pre-scripts on each endpoint before formatting.
        // Batch path uses folder+class scopes only (no per-endpoint inline scripts) —
        // PreScriptApplier is EDT-free, safe to call from this background coroutine.
        val scriptedEndpoints = if (runPreScripts && resolved.first.isNotEmpty()) {
            val applier = PreScriptApplier.getInstance(project)
            resolved.first.map { ep ->
                val scopes = CurlScriptScopes.resolveFolderAndClassScopes(ep)
                applier.applyScripts(ep, resolved.second, scopes)
            }
        } else {
            resolved.first
        }

        val content = CurlFormatter.formatAll(scriptedEndpoints, resolved.second, options)

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
        val (outputDir, fileName) = when (config) {
            is CurlConfig -> config.outputDir to config.fileName
            is ChannelConfig.FileConfig -> config.outputDir to config.fileName
            else -> null to null
        }

        val targetFile = resolveTargetFile(project, outputDir, fileName, "curl_commands.sh")
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
        outputDir: String?,
        fileName: String?,
        defaultFileName: String,
    ): File? {
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
