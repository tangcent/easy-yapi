package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.Settings
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Dedicated **AI** settings tab.
 *
 * Previously the AI assistant section was buried at the bottom of the
 * "Other" tab. It is promoted to its own top-level tab so users
 * can find provider / API-key / model configuration without scrolling
 * past unrelated import-export controls.
 *
 * The panel is a thin wrapper around [AiAssistantSection] — the form
 * logic, test-connection flow, and auto-detect flow all live there.
 */
class AiSettingsPanel : SettingsPanel {

    private val section = AiAssistantSection()

    override val component: JComponent = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        add(section.component, BorderLayout.NORTH)
    }

    override fun resetFrom(settings: Settings?) {
        section.resetFrom(settings)
    }

    override fun applyTo(settings: Settings) {
        section.applyTo(settings)
    }

    override fun isModified(settings: Settings?): Boolean {
        return section.isModified(settings)
    }
}
