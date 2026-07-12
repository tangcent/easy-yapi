package com.itangcent.easyapi.exporter.channel

import com.itangcent.easyapi.config.DOLLAR_BRACE_PATTERN

/**
 * Target variable syntax for unresolved `${...}` placeholders.
 *
 * Each platform renders unresolved environment-variable references using a
 * distinct delimiter pair; this enum captures the open/close tokens so the
 * converter stays platform-agnostic.
 *
 * @property open The opening delimiter of the platform's variable syntax.
 * @property close The closing delimiter of the platform's variable syntax.
 */
enum class PlaceholderTargetSyntax(val open: String, val close: String) {
    POSTMAN("{{", "}}"),
    HOPPSCOTCH("<<", ">>"),
}

/**
 * Converts unresolved `${name}` placeholders in a header/param value to the
 * target platform's variable syntax.
 *
 * Resolved placeholders (config-backed) and regex-capture `${1}` are left
 * untouched so the converter never breaks an existing single-app export
 * (byte-parity fast path) nor a path-parameter capture reference.
 *
 * Pure: no `Project` / `EnvironmentService` / `ConfigReader` dependency. The
 * caller supplies [isResolvable] (typically
 * `ConfigReader.getInstance(project).getFirst(name).isNullOrEmpty().not()`),
 * mirroring the [com.itangcent.easyapi.exporter.channel.curl.EndpointVariableResolver]
 * shape (pure object, caller-supplied lookup).
 *
 * Reuses the existing [DOLLAR_BRACE_PATTERN] from `com.itangcent.easyapi.config`
 * (the same regex the config layer uses for variable resolution) — no new
 * regex is introduced.
 */
object PlaceholderSyntaxConverter {

    /** Matches `${1}`, `${12}`, … — a regex-capture reference, not a variable. */
    private val REGEX_CAPTURE_PATTERN = Regex("^\\d+$")

    /**
     * Converts unresolved `${name}` placeholders in [value] to [target]'s syntax.
     *
     * Behavior:
     * 1. **Fast path** — if [value] contains no `${`, it is returned unchanged
     *    (same instance) to preserve byte-parity for placeholder-free values.
     * 2. For each `${name}` match (via [DOLLAR_BRACE_PATTERN]):
     *    - skip if `name` matches `^\d+$` (regex-capture reference, e.g. `${1}`);
     *    - leave `${name}` as-is if [isResolvable] returns `true` (the config
     *      layer will substitute it);
     *    - otherwise replace with `target.open + name + target.close`.
     * 3. Already-present `{{...}}` / `<<...>>` are never touched — only `${...}`
     *    is rewritten.
     *
     * A malformed `${` with no closing `}` is not matched by the regex and is
     * left as-is. This function never throws.
     *
     * @param value The header/param value possibly containing `${...}`.
     * @param target The target platform variable syntax.
     * @param isResolvable Oracle returning `true` when the config layer can
     *   substitute `name` (i.e. it should be left as `${name}`).
     */
    fun convert(
        value: String,
        target: PlaceholderTargetSyntax,
        isResolvable: (String) -> Boolean,
    ): String {
        // Fast path: no placeholder to convert — return the same instance.
        if (!value.contains("\${")) return value

        return DOLLAR_BRACE_PATTERN.replace(value) { match ->
            val name = match.groupValues[1].trim()
            if (REGEX_CAPTURE_PATTERN.matches(name)) {
                // Regex-capture reference (e.g. `${1}`) — leave untouched.
                return@replace match.value
            }
            if (isResolvable(name)) {
                // Config layer will substitute this — leave as `${name}`.
                return@replace match.value
            }
            // Unresolved — rewrite to the target platform syntax.
            target.open + name + target.close
        }
    }
}
