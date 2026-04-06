package com.itangcent.easyapi.ide.search

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.model.path
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * Renders API endpoint search results with color-coded HTTP method badges.
 *
 * Displays each endpoint with:
 * - HTTP method badge (color-coded: GET=green, POST=blue, PUT=orange, DELETE=red, etc.)
 * - API path in bold
 * - Endpoint name (if available) in gray
 * - Class name on the right side
 *
 * @see ApiSearchEverywhereContributor for the search contributor
 */
class ApiSearchResultRenderer : ListCellRenderer<ApiEndpoint> {

    private val methodColors = mapOf(
        HttpMethod.GET to JBColor(Color(0, 128, 0), Color(0, 180, 0)),
        HttpMethod.POST to JBColor(Color(0, 102, 204), Color(51, 153, 255)),
        HttpMethod.PUT to JBColor(Color(255, 153, 0), Color(255, 179, 51)),
        HttpMethod.DELETE to JBColor(Color(204, 0, 0), Color(255, 51, 51)),
        HttpMethod.PATCH to JBColor(Color(128, 0, 128), Color(178, 102, 178)),
        HttpMethod.HEAD to JBColor(Color(128, 128, 128), Color(160, 160, 160)),
        HttpMethod.OPTIONS to JBColor(Color(128, 128, 128), Color(160, 160, 160)),
        HttpMethod.NO_METHOD to JBColor(Color(128, 128, 128), Color(160, 160, 160))
    )

    override fun getListCellRendererComponent(
        list: JList<out ApiEndpoint>?,
        value: ApiEndpoint,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 8)
            background = if (isSelected) list?.selectionBackground else list?.background
        }

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
        }

        val httpMeta = value.httpMetadata
        val methodLabel = if (httpMeta != null) {
            createMethodLabel(httpMeta.method)
        } else {
            JBLabel(value.metadata.protocol.padEnd(7)).apply {
                foreground = JBColor.GRAY
                font = font.deriveFont(java.awt.Font.BOLD, font.size2D + 1)
                border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
            }
        }
        leftPanel.add(methodLabel)

        val pathLabel = JBLabel(value.path).apply {
            font = font.deriveFont(font.size2D + 1)
        }
        leftPanel.add(pathLabel)

        if (!value.name.isNullOrBlank()) {
            val nameLabel = JBLabel(" - ${value.name}").apply {
                foreground = JBColor.GRAY
            }
            leftPanel.add(nameLabel)
        }

        panel.add(leftPanel, BorderLayout.CENTER)

        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
        }

        if (!value.className.isNullOrBlank()) {
            val classLabel = JBLabel(value.className).apply {
                foreground = JBColor.GRAY
                font = font.deriveFont(font.size2D - 1)
            }
            rightPanel.add(classLabel)
        }

        panel.add(rightPanel, BorderLayout.EAST)

        return panel
    }

    private fun createMethodLabel(method: HttpMethod): JBLabel {
        val color = methodColors[method] ?: JBColor.GRAY
        val text = method.name.padEnd(7)
        
        return JBLabel(text).apply {
            foreground = color
            font = font.deriveFont(java.awt.Font.BOLD, font.size2D + 1)
            border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        }
    }
}
