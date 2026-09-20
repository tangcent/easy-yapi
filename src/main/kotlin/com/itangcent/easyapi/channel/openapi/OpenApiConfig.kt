package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.core.logging.IdeaLog

/**
 * Selected output format for the OpenAPI channel.
 *
 * - `JSON` — serialize via Gson (default-of-defaults when stored values are
 *   unrecognized, see [parseOutputFormat]).
 * - `YAML` — serialize via Jackson `YAMLMapper`.
 * - `ALWAYS_ASK` — prompt the user on EDT with a `MessagesService.showChooseDialog`
 *   for JSON / YAML at export time. Default for both `OpenApiConfig` and
 *   `OpenApiSettings`.
 *
 * Defined early so that the formatter / channel can already reference a
 * stable type.
 */
enum class OpenApiOutputFormat { JSON, YAML, ALWAYS_ASK }

/**
 * Per-export configuration for the OpenAPI channel.
 *
 * Built by `OpenApiOptionsPanel` from the export dialog. For the quick-export
 * path (no options panel shown), the effective config is built directly from
 * `OpenApiSettings.outputFormat` via [parseOutputFormat].
 *
 * The v1 `infoTitle` / `infoVersion` /
 * `infoDescription` / `serverUrl` fields were removed — those values vary
 * per project and belong in rule scripts (see `swagger3-openapi.config` /
 * `swagger-openapi.config` / `springfox-openapi.config`). The channel now
 * carries rule-resolved envelope metadata in an internal [OpenApiEnvelope]
 * between channel and formatter.
 *
 * `outputFormat` is the only field here — it is consumed by
 * `OpenApiChannel.export` to pick the serializer. `OpenApiFormatter` does not
 * read it.
 */
data class OpenApiConfig(
    val outputFormat: OpenApiOutputFormat = OpenApiOutputFormat.ALWAYS_ASK,
) : ChannelConfig() {

    companion object : IdeaLog {

        /**
         * Parses the persistent [OpenApiSettings.outputFormat] string into the
         * enum. Falls back to JSON and logs a warning when the stored value is
         * unrecognized (e.g. corrupted state or a future format name).
         *
         * Note: `ALWAYS_ASK` is a valid stored value (default for new
         * installations); only truly unrecognized values fall back.
         */
        internal fun parseOutputFormat(value: String): OpenApiOutputFormat =
            runCatching { OpenApiOutputFormat.valueOf(value.uppercase()) }
                .onFailure { LOG.warn("Unrecognized outputFormat '$value', defaulting to JSON", it) }
                .getOrDefault(OpenApiOutputFormat.JSON)
    }
}
