package com.itangcent.easyapi.core.export

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.channel.spi.ChannelOption

/** What a [ProbeChannel] accepts: a secret and a plain option. */
internal val PROBE_OPTIONS = listOf(
    ChannelOption("token", "A secret token."),
    ChannelOption("space", "Where the items go.")
)

/**
 * A channel that can export headless, for the tests of a headless export: it records what it is handed, and
 * [onExport] decides what it reports and returns (by default nothing, and a success for every endpoint).
 * It accepts [PROBE_OPTIONS] and builds a [ProbeConfig] from them. A test registers it on the `channel`
 * extension point with its `testRootDisposable`, so it is dropped when the test ends.
 */
internal class ProbeChannel(
    override val id: String,
    override val enabledByDefault: Boolean = true,
    private val onExport: suspend (ExportContext) -> ExportResult = {
        ExportResult.Success(it.endpointsToExport.size, "probe")
    }
) : Channel {
    override val displayName: String = "Probe"
    override val supportsHeadless: Boolean = true

    /** The context of every export, in call order. */
    val exports = mutableListOf<ExportContext>()

    /** The options of every [buildConfig] call, in call order. */
    val configs = mutableListOf<Map<String, String>>()

    /** How many times [handleResult] was called. */
    var handled = 0

    override fun exportOptions(): List<ChannelOption> = PROBE_OPTIONS

    override fun buildConfig(options: Map<String, String>): ChannelConfig {
        configs += options
        return ProbeConfig(options)
    }

    override suspend fun export(context: ExportContext): ExportResult {
        exports += context
        return onExport(context)
    }

    override suspend fun handleResult(project: Project, result: ExportResult.Success, config: ChannelConfig): Boolean {
        handled++
        return false
    }
}

/** The config a [ProbeChannel] builds: the options it was given. */
internal data class ProbeConfig(val options: Map<String, String>) : ChannelConfig()
