package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.itangcent.easyapi.exporter.channel.ApiChannelRegistry
import com.itangcent.easyapi.logging.IdeaLog

class ChannelQuickActionGroup : ActionGroup(), IdeaLog {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return emptyArray()
        val registry = ApiChannelRegistry.getInstance(project)
        return registry.getActionChannels()
            .map { channel -> ChannelExportAction(channel.id, channel.actionText ?: channel.displayName) }
            .toTypedArray()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
