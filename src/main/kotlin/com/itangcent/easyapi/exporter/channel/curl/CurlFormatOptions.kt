package com.itangcent.easyapi.exporter.channel.curl

/**
 * Formatting options for [CurlFormatter].
 *
 * All defaults preserve the pre-enhancement output byte-for-byte:
 * - [includeComments] `true`  → matches current `formatAll` (emits `## name` + `---`)
 * - [prettyPrintBody] `false` → current output is compact JSON
 * - [multiLineFormat] `false` → current output is single-line
 * - [longFlags] `false`       → current output uses `-X`/`-H`/`-d`/`-F`
 * - [includeResponseExample] `false` → current output has no response comment
 *
 * @property includeComments Include `## API Name` comments and `---` section dividers in `formatAll`.
 * @property prettyPrintBody Format JSON body payload with 2-space indentation.
 * @property multiLineFormat Place each flag on its own line with `\` continuation.
 * @property longFlags Use `--request`/`--header`/`--data`/`--form` instead of `-X`/`-H`/`-d`/`-F`.
 * @property includeResponseExample Append `# Response: <json>` comment when `responseBody` is present.
 */
data class CurlFormatOptions(
    val includeComments: Boolean = true,
    val prettyPrintBody: Boolean = false,
    val multiLineFormat: Boolean = false,
    val longFlags: Boolean = false,
    val includeResponseExample: Boolean = false,
)
