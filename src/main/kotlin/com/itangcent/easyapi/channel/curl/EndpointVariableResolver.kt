package com.itangcent.easyapi.channel.curl

import com.itangcent.easyapi.core.config.DOUBLE_BRACE_PATTERN
import com.itangcent.easyapi.core.config.DOLLAR_BRACE_PATTERN
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.GrpcMetadata
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.MutableExtension
import com.itangcent.easyapi.format.json.ObjectModelJsonConverter

/**
 * Result of resolving `{{var}}` / `${var}` placeholders in an [ApiEndpoint] + host string.
 *
 * @property endpoint Deep copy with resolved string fields; the original is untouched.
 * @property host Resolved host string.
 * @property resolved Variable names that were successfully resolved (from either source).
 * @property missing Variable names that no source could resolve (placeholders left as-is).
 */
data class ResolutionResult(
    val endpoint: ApiEndpoint,
    val host: String,
    val resolved: Set<String>,
    val missing: Set<String>,
)

/**
 * Pure placeholder resolver for [ApiEndpoint] + host strings.
 *
 * No `Project` / `EnvironmentService` / `ConfigReader` dependency — the caller assembles
 * [variables] and [fallback] (unit-testable with plain JUnit, no IntelliJ fixture).
 *
 * Reuses the existing [DOUBLE_BRACE_PATTERN] / [DOLLAR_BRACE_PATTERN] regexes from
 * `com.itangcent.easyapi.config` (the same patterns [com.itangcent.easyapi.core.dashboard.RequestExecutor]
 * uses for variable resolution).
 *
 * ## Body JSON handling
 *
 * `ObjectModel` bodies ([HttpMetadata.body], [GrpcMetadata.body]) are resolved by serializing
 * to JSON, resolving placeholders in the JSON string, and stashing the result in
 * [ApiEndpoint.extensions] under [RESOLVED_BODY_JSON_KEY]. The downstream formatter reads
 * this key first; if absent, it serializes the (unresolved) body itself. This keeps the
 * resolver pure (no formatter dependency) and the formatter pure (no resolution logic).
 */
object EndpointVariableResolver {

    /** Extension key carrying the resolved body JSON, if the resolver produced one. */
    const val RESOLVED_BODY_JSON_KEY = "curl.resolvedBodyJson"

    /**
     * Resolves `{{var}}` and `${var}` placeholders in [endpoint] and [host].
     *
     * For each placeholder: look up [variables] first, then [fallback]. If neither resolves,
     * the placeholder is left as-is and the variable name is added to `missing`.
     *
     * The input [endpoint] is NOT mutated; a deep copy is returned.
     */
    fun resolve(
        endpoint: ApiEndpoint,
        host: String,
        variables: Map<String, String>,
        fallback: (String) -> String?,
    ): ResolutionResult {
        val resolved = mutableSetOf<String>()
        val missing = mutableSetOf<String>()

        fun resolveStr(s: String?): String? {
            if (s == null) return null
            if (!s.contains("{{") && !s.contains("\${")) return s
            return replacePlaceholders(s, variables, fallback, resolved, missing)
        }

        val resolvedHost = resolveStr(host) ?: host
        val resolvedEndpoint = when (val meta = endpoint.metadata) {
            is HttpMetadata -> {
                val resolvedBodyJson = meta.body?.let {
                    val compact = ObjectModelJsonConverter.toJson(it)
                    resolveStr(compact)
                }
                endpoint.copy(
                    metadata = meta.copy(
                        path = resolveStr(meta.path) ?: meta.path,
                        headers = meta.headers.map { h ->
                            h.copy(
                                name = resolveStr(h.name) ?: h.name,
                                value = resolveStr(h.value),
                                example = resolveStr(h.example),
                            )
                        }.toMutableList(),
                        parameters = meta.parameters.map { p ->
                            p.copy(
                                name = resolveStr(p.name) ?: p.name,
                                defaultValue = resolveStr(p.defaultValue),
                                example = resolveStr(p.example),
                            )
                        }.toMutableList(),
                        contentType = resolveStr(meta.contentType),
                    ),
                    extensions = withNewExtension(endpoint, resolvedBodyJson),
                )
            }
            is GrpcMetadata -> {
                val resolvedBodyJson = meta.body?.let {
                    val compact = ObjectModelJsonConverter.toJson(it)
                    resolveStr(compact)
                }
                endpoint.copy(
                    metadata = meta.copy(
                        path = resolveStr(meta.path) ?: meta.path,
                        serviceName = resolveStr(meta.serviceName) ?: meta.serviceName,
                        methodName = resolveStr(meta.methodName) ?: meta.methodName,
                        packageName = resolveStr(meta.packageName) ?: meta.packageName,
                        protoFile = resolveStr(meta.protoFile),
                    ),
                    extensions = withNewExtension(endpoint, resolvedBodyJson),
                )
            }
            else -> endpoint
        }
        return ResolutionResult(resolvedEndpoint, resolvedHost, resolved.toSet(), missing.toSet())
    }

    /**
     * Resolves placeholders in a single string. Exposed for callers that need to resolve
     * a standalone string (e.g. the host) without building a full [ApiEndpoint].
     */
    fun resolveString(
        s: String,
        variables: Map<String, String>,
        fallback: (String) -> String?,
    ): String {
        if (!s.contains("{{") && !s.contains("\${")) return s
        val resolved = mutableSetOf<String>()
        val missing = mutableSetOf<String>()
        return replacePlaceholders(s, variables, fallback, resolved, missing)
    }

    private fun replacePlaceholders(
        input: String,
        variables: Map<String, String>,
        fallback: (String) -> String?,
        resolved: MutableSet<String>,
        missing: MutableSet<String>,
    ): String {
        var result = DOLLAR_BRACE_PATTERN.replace(input) { match ->
            val key = match.groupValues[1].trim()
            resolveOne(key, variables, fallback, resolved, missing) ?: match.value
        }
        result = DOUBLE_BRACE_PATTERN.replace(result) { match ->
            val key = match.groupValues[1].trim()
            resolveOne(key, variables, fallback, resolved, missing) ?: match.value
        }
        return result
    }

    private fun resolveOne(
        key: String,
        variables: Map<String, String>,
        fallback: (String) -> String?,
        resolved: MutableSet<String>,
        missing: MutableSet<String>,
    ): String? {
        val fromEnv = variables[key]
        if (fromEnv != null) {
            resolved += key
            return fromEnv
        }
        val fromFallback = fallback(key)
        if (fromFallback != null) {
            resolved += key
            return fromFallback
        }
        missing += key
        return null
    }

    /** Builds a fresh [MutableExtension] copying [endpoint]'s existing entries + the resolved body JSON (if any). */
    private fun withNewExtension(endpoint: ApiEndpoint, resolvedBodyJson: String?): MutableExtension {
        val mutable = MutableExtension()
        mutable.putAll(endpoint.extensions.exts)
        if (resolvedBodyJson != null) {
            mutable[RESOLVED_BODY_JSON_KEY] = resolvedBodyJson
        }
        return mutable
    }
}
