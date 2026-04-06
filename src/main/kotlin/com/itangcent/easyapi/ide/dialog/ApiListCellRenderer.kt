package com.itangcent.easyapi.ide.dialog

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.model.path
import java.awt.Color
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * List cell renderer for displaying API endpoints with color-coded HTTP methods.
 *
 * Renders each endpoint as "METHOD path - name" with the method name colored
 * according to standard HTTP method conventions:
 * - GET: Blue (#61affe)
 * - POST: Green (#49cc90)
 * - PUT: Orange (#fca130)
 * - DELETE: Red (#f93e3e)
 * - PATCH: Cyan (#50e3c2)
 * - HEAD: Purple (#9012fe)
 * - OPTIONS: Dark Blue (#0d5aa7)
 *
 * @see ApiEndpoint for the data model
 */
class ApiListCellRenderer : JLabel(), ListCellRenderer<ApiEndpoint> {

    init {
        isOpaque = true
    }

    override fun getListCellRendererComponent(
        list: JList<out ApiEndpoint>?,
        value: ApiEndpoint?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        if (value == null) {
            text = ""
            return this
        }

        val method = (value.httpMetadata?.method?.name ?: value.metadata.protocol).padEnd(6)
        val path = value.path
        val name = value.name?.let { " - $it" } ?: ""

        text = "$method $path$name"
        foreground = if (isSelected) {
            list?.selectionForeground ?: Color.WHITE
        } else {
            val httpMeta = value.httpMetadata
            if (httpMeta != null) getMethodColor(httpMeta.method) else Color(0x999999)
        }
        background = if (isSelected) {
            list?.selectionBackground ?: Color.BLUE
        } else {
            list?.background ?: Color.WHITE
        }

        return this
    }

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
