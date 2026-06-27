package com.itangcent.easyapi.exporter.channel.yapi

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.ide.BrowserUtil
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.channel.ApiChannel
import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.channel.ChannelOptionsPanel
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.yapi.YapiExporter
import com.itangcent.easyapi.exporter.yapi.YapiExportMetadata
import com.itangcent.easyapi.ide.support.NotificationUtils
import com.itangcent.easyapi.logging.IdeaLog

class YapiChannel : ApiChannel, IdeaLog {

    override val id: String = "yapi"
    override val displayName: String = "Yapi"
    override val supportsGrpc: Boolean = false
    override val exposeAsAction: Boolean = true
    override val actionText: String = "Export to YAPI"

    override fun createOptionsPanel(project: Project): ChannelOptionsPanel {
        return YapiOptionsPanel(project)
    }

    override suspend fun export(context: ExportContext): ExportResult {
        LOG.info("YapiChannel.export: endpoints=${context.endpointsToExport.size}")
        val yapiConfig = context.channelConfig as? ChannelConfig.YapiConfig
        val exporter = YapiExporter.getInstance(context.project)
        return exporter.export(context, yapiConfig?.selectedToken)
    }

    override suspend fun handleResult(
        project: Project,
        result: ExportResult.Success,
        config: ChannelConfig
    ): Boolean {
        val metadata = result.metadata as? YapiExportMetadata ?: return false
        swing {
            // Route through NotificationUtils so the group id and mirror policy are centralized.
            NotificationUtils.notifyInfoWithConfig(
                project = project,
                title = "Export to YAPI",
                content = "Exported ${result.count} endpoints to YAPI"
            ) {
                for ((cartName, cartUrl) in metadata.cartLinks) {
                    addAction(object : NotificationAction(cartName) {
                        override fun actionPerformed(
                            e: AnActionEvent,
                            notification: Notification
                        ) {
                            BrowserUtil.browse(cartUrl)
                        }
                    })
                }
            }
        }
        return true
    }
}
