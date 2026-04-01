package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMethod
import java.awt.Color
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

/**
 * Custom tree cell renderer for API endpoint nodes in the dashboard tree.
 * 
 * This renderer provides:
 * - Color-coded HTTP method labels (GET=blue, POST=green, etc.)
 * - Formatted display text showing method, name, and path
 * - Visual distinction between different HTTP methods
 */
class ApiTreeCellRenderer : DefaultTreeCellRenderer() {

    /**
     * Customizes the appearance of tree cells based on their content.
     * For API endpoint nodes, displays the HTTP method with appropriate coloring.
     * 
     * @param tree The tree component
     * @param value The node value (DefaultMutableTreeNode or ApiEndpoint)
     * @param sel Whether the cell is selected
     * @param expanded Whether the node is expanded
     * @param leaf Whether the node is a leaf
     * @param row The row index
     * @param hasFocus Whether the cell has focus
     * @return The configured renderer component
     */
    override fun getTreeCellRendererComponent(
        tree: JTree?,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        val component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
        
        val endpoint = when (value) {
            is DefaultMutableTreeNode -> value.userObject as? ApiEndpoint
            is ApiEndpoint -> value
            else -> null
        }
        
        if (component is JLabel && endpoint != null) {
            component.text = buildApiText(endpoint)
            component.foreground = getMethodColor(endpoint.method)
        }
        
        return component
    }

    /**
     * Builds the display text for an API endpoint.
     * Format: "METHOD name [path]" or "METHOD path" if no name.
     * 
     * @param endpoint The API endpoint to format
     * @return Formatted display string
     */
    private fun buildApiText(endpoint: ApiEndpoint): String {
        return buildString {
            append(endpoint.method.name)
            append(" ")
            append(endpoint.name ?: endpoint.path)
            endpoint.path.let { path ->
                if (endpoint.name != null && endpoint.name != path) {
                    append(" [")
                    append(path)
                    append("]")
                }
            }
        }
    }

    /**
     * Returns the color associated with an HTTP method.
     * Colors follow common API documentation conventions:
     * - GET: Blue (#61affe)
     * - POST: Green (#49cc90)
     * - PUT: Orange (#fca130)
     * - DELETE: Red (#f93e3e)
     * - PATCH: Cyan (#50e3c2)
     * - HEAD: Purple (#9012fe)
     * - OPTIONS: Dark blue (#0d5aa7)
     * 
     * @param method The HTTP method
     * @return The associated color
     */
    private fun getMethodColor(method: HttpMethod): Color {
        return when (method) {
            HttpMethod.GET -> Color(0x61affe)
            HttpMethod.POST -> Color(0x49cc90)
            HttpMethod.PUT -> Color(0xfca130)
            HttpMethod.DELETE -> Color(0xf93e3e)
            HttpMethod.PATCH -> Color(0x50e3c2)
            HttpMethod.HEAD -> Color(0x9012fe)
            HttpMethod.OPTIONS -> Color(0x0d5aa7)
            HttpMethod.NO_METHOD -> Color(0x999999)
        }
    }
}
