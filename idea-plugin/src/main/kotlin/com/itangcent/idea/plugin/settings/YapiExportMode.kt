package com.itangcent.idea.plugin.settings

/**
 * Used to indicate whether to update or skip an API when
 * it is already existed in the project.
 *
 * @author tangcent
 */
enum class YapiExportMode(var desc: String) {
    /**
     * Indicates that the apis will to be <b>always</b>
     * updated regardless whether the api is already existed
     * in yapi server or not.
     */
    ALWAYS_UPDATE("always update existed api"),
    NEVER_UPDATE("never update existed api"),
    ALWAYS_ASK("always popup a window to ask whether to override the api"),
    UPDATE_IF_CHANGED("update only if the API content has changed")
}