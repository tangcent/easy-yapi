package com.itangcent.idea.plugin.settings

/**
 * @author tangcent
 */
enum class PostmanExportMode(var desc: String) {
    COPY("always create new collection"),
    UPDATE("try update existed collection")
}