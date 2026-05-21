package com.itangcent.easyapi.exporter.channel

import javax.swing.JComponent

/**
 * UI panel for configuring an [ApiChannel] before export.
 *
 * Implementations are created by [ApiChannel.createOptionsPanel] and shown
 * in the export dialog when the user selects a channel.
 *
 * ## Implementing
 *
 * - Provide a Swing [component] that contains the configuration UI.
 * - Override [buildConfig] to collect the user's input into a [ChannelConfig].
 * - Optionally override [onShown] to initialize the panel when it becomes visible.
 */
interface ChannelOptionsPanel {

    /** The Swing component to display in the export dialog. */
    val component: JComponent

    /**
     * Collects the current panel state into a [ChannelConfig].
     *
     * Called when the user clicks "Export" in the dialog.
     *
     * @return the channel configuration based on the current UI state
     */
    fun buildConfig(): ChannelConfig

    /**
     * Called when the panel becomes visible in the dialog.
     * Use this to initialize defaults or refresh state.
     */
    fun onShown() {}
}
