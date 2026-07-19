package com.itangcent.easyapi.core.settings.ui

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.TitledBorder

/**
 * Shared UI builders for settings panels.
 *
 * Centralizes the `TitledBorder` panel pattern used across [SettingsPanel]
 * implementations (gRPC, Environments, General, Parsing & Output, AI) so the
 * styling stays consistent and panels stop re-inlining the same boilerplate.
 */
internal object SettingsUiKit {

    /**
     * A panel with an etched [TitledBorder] and a vertical stack of components.
     */
    fun titledPanel(title: String, components: List<JComponent>): JPanel {
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP
            )
            val inner = JPanel(GridLayout(0, 1, 0, 2))
            components.forEach { inner.add(it) }
            add(inner, BorderLayout.CENTER)
        }
    }

    /**
     * A titled panel wrapping a single arbitrary component (used when the inner
     * layout is not a simple vertical stack — e.g. a table + toolbar, or a
     * composite sub-panel).
     */
    fun titledPanel(title: String, component: JComponent): JPanel {
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP
            )
            add(component, BorderLayout.CENTER)
        }
    }

    /**
     * A left-aligned label + field row. The label is given a fixed width so
     * multiple rows line up. [extras] (e.g. an eye-toggle button) are appended
     * after the field.
     */
    fun labeledRow(label: String, field: JComponent, vararg extras: JComponent): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2))
        if (label.isNotEmpty()) {
            val l = JLabel(label)
            l.preferredSize = Dimension(150, l.preferredSize.height)
            panel.add(l)
        }
        panel.add(field)
        extras.forEach { panel.add(it) }
        return panel
    }
}
