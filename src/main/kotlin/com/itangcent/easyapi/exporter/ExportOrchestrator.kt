package com.itangcent.easyapi.exporter

import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.cache.api.ApiIndex
import com.itangcent.easyapi.dashboard.ApiScanner
import com.itangcent.easyapi.exporter.channel.ApiChannelRegistry
import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.ide.support.NotificationUtils
import com.itangcent.easyapi.ide.support.SelectionScope
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.settings.settings
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class ExportOrchestrator(private val project: Project) : IdeaLog {

    private val apiScanner: ApiScanner = ApiScanner.getInstance(project)
    private val apiIndex: ApiIndex = ApiIndex.getInstance(project)
    private val channelRegistry: ApiChannelRegistry = ApiChannelRegistry.getInstance(project)

    companion object {
        fun getInstance(project: Project): ExportOrchestrator {
            return project.getService(ExportOrchestrator::class.java)
        }
    }

    suspend fun orchestrateExport(
        selection: SelectionScope?,
        channelId: String,
        channelConfig: ChannelConfig = ChannelConfig.Empty,
        indicator: ProgressIndicator? = null
    ): ExportResult {
        LOG.info("ExportOrchestrator.orchestrateExport: channelId=$channelId, selection=$selection")
        val channel = channelRegistry.getChannel(channelId)
            ?: return ExportResult.Error("No channel registered for id: $channelId")

        indicator?.text = "Scanning for API endpoints..."
        indicator?.isIndeterminate = true
        val endpoints = scanEndpoints(selection, indicator)

        if (endpoints.isEmpty()) {
            NotificationUtils.notifyWarning(project, "Export", "No API endpoints found in selection")
            return ExportResult.Error("No API endpoints found")
        }

        NotificationUtils.notifyInfo(project, "Export", "Exporting ${endpoints.size} endpoints via ${channel.displayName}")
        indicator?.text = "Exporting ${endpoints.size} endpoints via ${channel.displayName}..."
        indicator?.isIndeterminate = false
        indicator?.fraction = 0.0

        val settings = project.settings
        val context = ExportContext(
            project = project,
            endpoints = endpoints,
            settings = settings,
            channelId = channelId,
            channelConfig = channelConfig,
            indicator = indicator
        )

        val result = channel.export(context)
        if (result is ExportResult.Success) {
            val handled = channel.handleResult(project, result, channelConfig)
            if (!handled) {
                swing {
                    Messages.showInfoMessage(
                        project,
                        "Successfully exported ${result.count} endpoints to ${result.target}",
                        "Export Successful"
                    )
                }
            }
        } else if (result is ExportResult.Error) {
            NotificationUtils.notifyError(project, "Export", "Channel $channelId failed: ${result.message}")
        }
        return result
    }

    suspend fun exportViaChannel(
        channelId: String,
        endpoints: List<ApiEndpoint>,
        channelConfig: ChannelConfig = ChannelConfig.Empty,
        indicator: ProgressIndicator? = null
    ): ExportResult {
        LOG.info("ExportOrchestrator.exportViaChannel: channelId=$channelId, endpoints=${endpoints.size}")
        val channel = channelRegistry.getChannel(channelId)
            ?: return ExportResult.Error("No channel registered for id: $channelId")

        indicator?.text = "Exporting ${endpoints.size} endpoints via ${channel.displayName}..."
        indicator?.isIndeterminate = false
        indicator?.fraction = 0.0

        val settings = project.settings
        val context = ExportContext(
            project = project,
            endpoints = endpoints,
            settings = settings,
            channelId = channelId,
            channelConfig = channelConfig,
            indicator = indicator
        )

        val result = channel.export(context)
        if (result is ExportResult.Success) {
            val handled = channel.handleResult(project, result, channelConfig)
            if (!handled) {
                swing {
                    Messages.showInfoMessage(
                        project,
                        "Successfully exported ${result.count} endpoints to ${result.target}",
                        "Export Successful"
                    )
                }
            }
        } else if (result is ExportResult.Error) {
            LOG.warn("ExportOrchestrator.exportViaChannel: channel=$channelId failed: ${result.message}")
        }
        return result
    }

    private suspend fun scanEndpoints(
        selection: SelectionScope?,
        indicator: ProgressIndicator? = null
    ): List<ApiEndpoint> {
        if (selection != null) {
            val classes = withContext(IdeDispatchers.ReadAction) {
                selection.classes().toList()
            }
            if (classes.isNotEmpty()) {
                return apiScanner.scanClasses(classes, indicator).toList()
            }
        }
        return apiIndex.endpoints()
    }
}
