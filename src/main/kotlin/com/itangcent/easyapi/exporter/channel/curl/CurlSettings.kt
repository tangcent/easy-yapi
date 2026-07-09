package com.itangcent.easyapi.exporter.channel.curl

import com.itangcent.easyapi.settings.Scope
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.StorageScope

/**
 * cURL channel settings.
 *
 * All fields are APPLICATION scope, persisted via the unified
 * [com.itangcent.easyapi.settings.state.UnifiedAppSettingsState].
 *
 * Mirrors the [com.itangcent.easyapi.exporter.channel.hoppscotch.HoppscotchSettings]
 * shape: a `data class` with `@StorageScope(Scope.APPLICATION)` on every field,
 * read/written via [com.itangcent.easyapi.settings.SettingBinder] and
 * `project.settings<CurlSettings>()`.
 *
 * @property renderMode Controls when `{{var}}`/`${var}` placeholders in the
 *  endpoint are resolved against the active environment before formatting.
 *  Stored as a string (enum name) for serialization stability; use
 *  [renderModeEnum] for the typed value. Defaults to [CurlRenderMode.ALWAYS_ASK]
 *  so the user is prompted to pick an environment on each export (most discoverable).
 * @property copyFromEdited When true, the Dashboard "Copy as cURL" action uses the
 *  endpoint with dashboard edits applied (path/headers/params/body from the UI
 *  fields). When false (default), the original source-code endpoint is used.
 * @property includeComments Default value for the corresponding [CurlFormatOptions] flag.
 * @property prettyPrintBody Default value for the corresponding [CurlFormatOptions] flag.
 * @property multiLineFormat Default value for the corresponding [CurlFormatOptions] flag.
 * @property longFlags Default value for the corresponding [CurlFormatOptions] flag.
 * @property includeResponseExample Default value for the corresponding [CurlFormatOptions] flag.
 * @property runPreScripts When true, pre-request scripts (folder + API level) are run
 *  during cURL conversion so script-driven header injection / auth tokens / body
 *  rewriting are reflected in the generated command. Default `false`
 *  (no script machinery invoked, byte-identical output).
 */
data class CurlSettings(
    @StorageScope(Scope.APPLICATION) var renderMode: String = CurlRenderMode.ALWAYS_ASK.name,
    @StorageScope(Scope.APPLICATION) var copyFromEdited: Boolean = false,
    @StorageScope(Scope.APPLICATION) var includeComments: Boolean = true,
    @StorageScope(Scope.APPLICATION) var prettyPrintBody: Boolean = true,
    @StorageScope(Scope.APPLICATION) var multiLineFormat: Boolean = false,
    @StorageScope(Scope.APPLICATION) var longFlags: Boolean = false,
    @StorageScope(Scope.APPLICATION) var includeResponseExample: Boolean = false,
    @StorageScope(Scope.APPLICATION) var runPreScripts: Boolean = false,
) : Settings {

    /** Convenience: parse [renderMode] into the enum safely. */
    fun renderModeEnum(): CurlRenderMode = CurlRenderMode.fromStored(renderMode)

    /**
     * Maps the persistent formatting fields of this settings object to a
     * [CurlFormatOptions] instance. Centralizes the settings→options mapping so
     * every "copy as cURL" / single-endpoint render site (Dashboard, future
     * callers) derives options from one place — add a format flag here and it
     * flows everywhere automatically.
     *
     * Note: [renderMode] and [copyFromEdited] are *not* format options and are
     * intentionally excluded (they are consumed by [CurlExportResolver] and the
     * Copy action respectively).
     */
    fun toFormatOptions(): CurlFormatOptions = CurlFormatOptions(
        includeComments = includeComments,
        prettyPrintBody = prettyPrintBody,
        multiLineFormat = multiLineFormat,
        longFlags = longFlags,
        includeResponseExample = includeResponseExample,
    )
}
