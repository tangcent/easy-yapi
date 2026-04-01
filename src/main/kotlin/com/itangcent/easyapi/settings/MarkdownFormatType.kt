package com.itangcent.easyapi.settings

/**
 * Format types for Markdown output.
 *
 * Controls the level of detail in generated Markdown tables.
 *
 * @param desc Description of the format type
 */
enum class MarkdownFormatType(val desc: String) {
    /** Simple format with name, type, and description columns */
    SIMPLE("simple columns, include name、type、desc"),
    /** Ultimate format with additional required, default columns */
    ULTIMATE("more columns than simple, include name、type、required、default、desc")
}
