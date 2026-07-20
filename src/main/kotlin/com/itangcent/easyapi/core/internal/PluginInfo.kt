package com.itangcent.easyapi.core.internal

/**
 * Plugin identity and metadata constants for the EasyYapi IntelliJ plugin.
 *
 * Centralizes the plugin id (declared in `plugin.xml` as `<id>`) so that
 * extension-point name strings and `PluginId` lookups can reference a single
 * source of truth instead of hard-coding the raw id string at each call site.
 */
object PluginInfo {

    /**
     * The unique plugin id, matching `<id>` in `src/main/resources/META-INF/plugin.xml`.
     *
     * Used as the namespace prefix for all extension points declared by this
     * plugin — e.g. `"$PLUGIN_ID.channel"` resolves to
     * `"com.itangcent.idea.plugin.easy-yapi.channel"`.
     */
    const val PLUGIN_ID: String = "com.itangcent.idea.plugin.easy-yapi"
}
