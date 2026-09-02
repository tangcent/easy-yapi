package com.itangcent.easyapi.core.export

import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.easyapi.core.internal.threading.swing
import com.itangcent.easyapi.core.dashboard.ApiScanner
import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.ide.support.NotificationUtils
import com.itangcent.easyapi.core.ide.support.SelectionScope
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.rule.engine.RuleFailureMonitor

@Service(Service.Level.PROJECT)
class ExportOrchestrator(private val project: Project) : IdeaLog {

    private val apiScanner: ApiScanner = ApiScanner.getInstance(project)
    private val channelRegistry: ChannelRegistry = ChannelRegistry.getInstance(project)

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

        if (!channelRegistry.isEnabled(channel)) {
            LOG.info("ExportOrchestrator refusing disabled channel: $channelId")
            return ExportResult.Error("Channel '$channelId' is disabled. Enable it in Settings → EasyApi → General.")
        }

        // Bracket the whole run so throwing rules surface once at the end
        // instead of silently skipping endpoints.
        val failureMonitor = RuleFailureMonitor.getInstance(project)
        failureMonitor.beginRun()
        try {
            return orchestrate(channel, selection, channelConfig, indicator)
        } finally {
            failureMonitor.endRunAndNotify("Export")
        }
    }

    private suspend fun orchestrate(
        channel: Channel,
        selection: SelectionScope?,
        channelConfig: ChannelConfig,
        indicator: ProgressIndicator?
    ): ExportResult {
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

        val context = ExportContext(
            project = project,
            endpoints = endpoints,
            channelId = channel.id,
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
            NotificationUtils.notifyError(project, "Export", "Channel ${channel.id} failed: ${result.message}")
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

        if (!channelRegistry.isEnabled(channel)) {
            LOG.info("ExportOrchestrator refusing disabled channel: $channelId")
            return ExportResult.Error("Channel '$channelId' is disabled. Enable it in Settings → EasyApi → General.")
        }

        val failureMonitor = RuleFailureMonitor.getInstance(project)
        failureMonitor.beginRun()
        try {
            return exportViaChannel(channel, endpoints, channelConfig, indicator)
        } finally {
            failureMonitor.endRunAndNotify("Export")
        }
    }

    private suspend fun exportViaChannel(
        channel: Channel,
        endpoints: List<ApiEndpoint>,
        channelConfig: ChannelConfig,
        indicator: ProgressIndicator?
    ): ExportResult {
        indicator?.text = "Exporting ${endpoints.size} endpoints via ${channel.displayName}..."
        indicator?.isIndeterminate = false
        indicator?.fraction = 0.0

        val context = ExportContext(
            project = project,
            endpoints = endpoints,
            channelId = channel.id,
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
            LOG.warn("ExportOrchestrator.exportViaChannel: channel=${channel.id} failed: ${result.message}")
        }
        return result
    }

    private suspend fun scanEndpoints(
        selection: SelectionScope?,
        indicator: ProgressIndicator? = null
    ): List<ApiEndpoint> {
        // scanSelection respects method-level selections (issue #1407): when the
        // user selects specific controller methods, only those methods' endpoints
        // are returned instead of every endpoint in the containing class.
        return apiScanner.scanSelection(selection, indicator)
    }
}
