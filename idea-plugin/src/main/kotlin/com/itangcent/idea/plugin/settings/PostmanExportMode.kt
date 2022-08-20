package com.itangcent.idea.plugin.settings


/**
 * Used to indicate whether to create new collection or update existed collection when
 * export api to postman.
 *
 * @author tangcent
 */
enum class PostmanExportMode(var desc: String) {
    COPY("always create new collection"),
    UPDATE("try update existed collection")
}