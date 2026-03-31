package com.itangcent.easyapi.settings

/**
 * Export modes for YAPI platforms.
 *
 * Determines how existing APIs are handled during export.
 *
 * @param desc Description of the export mode
 */
enum class YapiExportMode(val desc: String) {
    /** Always update existing APIs */
    ALWAYS_UPDATE("always update existed api"),
    /** Never update existing APIs */
    NEVER_UPDATE("never update existed api"),
    /** Ask user for each conflict */
    ALWAYS_ASK("always popup a window to ask whether to override the api"),
    /** Update only when content has changed */
    UPDATE_IF_CHANGED("update only if the API content has changed")
}
