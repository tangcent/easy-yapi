package com.itangcent.easyapi.settings

/**
 * Export modes for Postman collections.
 *
 * Determines how collections are handled during export.
 *
 * @param desc Description of the export mode
 */
enum class PostmanExportMode(val desc: String) {
    /** Always create a new collection */
    CREATE_NEW("always create new collection"),
    /** Update existing collections by module */
    UPDATE_EXISTING("try update existed collection")
}
