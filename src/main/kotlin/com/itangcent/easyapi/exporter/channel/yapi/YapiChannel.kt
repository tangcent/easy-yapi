package com.itangcent.easyapi.exporter.channel.yapi

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.channel.ApiChannel
import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.channel.ChannelOptionsPanel
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.yapi.YapiExporter
import com.itangcent.easyapi.exporter.yapi.YapiExportMetadata

class YapiChannel : ApiChannel {

    override val id: String = "yapi"
    override val displayName: String = "Yapi"
    override val supportsGrpc: Boolean = false
    override val exposeAsAction: Boolean = true
    override val actionText: String = "Export to YAPI"

    override fun createOptionsPanel(project: Project): ChannelOptionsPanel {
        return YapiOptionsPanel(project)
    }

    override suspend fun export(context: ExportContext): ExportResult {
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
            val notification = com.intellij.notification.Notification(
                "EasyAPI Notifications",
                "Export to YAPI",
                "Exported ${result.count} endpoints to YAPI",
                com.intellij.notification.NotificationType.INFORMATION
            )
            for ((cartName, cartUrl) in metadata.cartLinks) {
                notification.addAction(object : com.intellij.notification.NotificationAction(cartName) {
                    override fun actionPerformed(
                        e: com.intellij.openapi.actionSystem.AnActionEvent,
                        notification: com.intellij.notification.Notification
                    ) {
                        com.intellij.ide.BrowserUtil.browse(cartUrl)
                    }
                })
            }
            com.intellij.notification.Notifications.Bus.notify(notification, project)
        }
        return true
    }
}
