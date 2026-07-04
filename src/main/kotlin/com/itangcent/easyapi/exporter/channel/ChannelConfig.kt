package com.itangcent.easyapi.exporter.channel

/**
 * Open class representing channel-specific configuration.
 *
 * Each [Channel] implementation may use a specific subclass to store
 * its configuration (e.g., output directory, workspace ID). Channel-specific
 * subtypes live in their channel's own package (e.g.
 * [com.itangcent.easyapi.exporter.channel.postman.PostmanConfig],
 * [com.itangcent.easyapi.exporter.channel.markdown.MarkdownConfig]) — adding a
 * channel with runtime config requires **zero core edits** (declare a new
 * `ChannelConfig` subclass in the channel's package).
 *
 * Formerly `sealed`; now `open` so channels can declare their own subtypes
 * without editing this core file. Consumers should use `as?` casts (not
 * `when`-exhaustive) when inspecting a [ChannelConfig].
 *
 * @see Channel.createOptionsPanel for the UI that creates these configs
 */
open class ChannelConfig {

    /** Placeholder for channels that require no configuration. */
    object Empty : ChannelConfig()

    /**
     * Configuration for file-based export channels.
     *
     * @property outputDir the target output directory, or `null` to use the default
     * @property fileName the output file name, or `null` to use the default
     */
    data class FileConfig(
        val outputDir: String? = null,
        val fileName: String? = null
    ) : ChannelConfig()
}
