package com.itangcent.easyapi.channel.curl

/**
 * Controls whether/when environment variable rendering occurs during cURL export.
 *
 * Mirrors the [com.itangcent.easyapi.channel.yapi.YapiExportMode] pattern:
 * each variant carries a human-readable [desc] used by settings UI and logs.
 *
 * - [NEVER_RENDER] — leave `{{var}}` / `${var}` placeholders untouched.
 * - [ALWAYS_RENDER] — resolve placeholders using the active environment, falling back to `ConfigReader`.
 * - [ALWAYS_ASK] — prompt the user to pick an environment (or skip) before each export.
 *
 * @param desc Description shown in the settings panel combo and log output.
 */
enum class CurlRenderMode(val desc: String) {
    /** Leave `{{var}}` / `${var}` placeholders untouched. */
    NEVER_RENDER("keep placeholders"),
    /** Resolve placeholders using the active environment, falling back to `ConfigReader`. */
    ALWAYS_RENDER("resolve with active environment"),
    /** Prompt the user to pick an environment (or skip) before each export. */
    ALWAYS_ASK("ask each export");

    companion object {
        /**
         * Parses a stored [value] (a String from settings) into the enum.
         * Returns [NEVER_RENDER] for null or unrecognized values (forward-compatible).
         */
        fun fromStored(value: String?): CurlRenderMode =
            value?.let { runCatching { valueOf(it) }.getOrNull() } ?: NEVER_RENDER
    }
}
