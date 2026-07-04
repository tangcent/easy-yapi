package com.itangcent.easyapi.exporter.channel.dummy

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.exporter.channel.Channel
import com.itangcent.easyapi.exporter.channel.ChannelOptionsPanel
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportResult

/**
 * V2 extensibility stub channel.
 *
 * A minimal [Channel] implementation that proves the Channel Author Contract:
 * a new channel plugs into the platform purely by implementing the [Channel]
 * extension point — **zero edits to any easy-api core/shared source file**.
 *
 * Lives under `src/test/` so it never ships in the production plugin. Registered
 * programmatically by [DummyChannelTest] via the `channel` extension point.
 */
class DummyChannel : Channel {

    override val id: String = "dummy"
    override val displayName: String = "Dummy"

    override fun createOptionsPanel(project: Project): ChannelOptionsPanel? = null

    override fun createSettingsPanel(project: Project): com.itangcent.easyapi.settings.ui.SettingsPanel<*>? = null

    override fun configFiles(): List<String> = emptyList()

    override suspend fun export(context: ExportContext): ExportResult =
        ExportResult.Success(0, "dummy")
}
