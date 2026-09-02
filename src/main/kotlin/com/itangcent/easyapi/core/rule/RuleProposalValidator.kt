package com.itangcent.easyapi.core.rule

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.core.config.model.bareKey
import com.itangcent.easyapi.core.config.model.filter
import com.itangcent.easyapi.core.config.parser.ConfigTextParser
import com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer
import com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer
import com.itangcent.easyapi.core.util.json.GsonUtils
import com.itangcent.easyapi.framework.spi.FrameworkRegistry

/**
 * Deterministic, PSI-free reviewer for AI-authored rule proposals (the
 * "review agent" is a fast local check, not an LLM).
 *
 * Catches the exact mechanical errors the preamble's CRITICAL sections warn
 * about but the model still produces: unknown rule keys, invalid filter
 * prefixes, and malformed JSON header/param values. Runs before a proposal
 * is staged (see [com.itangcent.easyapi.core.ai.tools.ProposeRuleContentTool]),
 * so a faulty proposal never reaches the user's Save dialog.
 *
 * Strictness policy (per product decision):
 * - **Hard errors** (block the proposal): unknown key, invalid filter prefix,
 *   malformed JSON value for the header/param keys.
 * - **Soft warnings** (never block): deprecated-but-valid filter forms such
 *   as the bare `class:` prefix; class-context `name()` calls that may be
 *   mistaken for fully-qualified names; `respondsTo(` probes that guess the
 *   context kind instead of calling `it.contextType()`; `canonicalText()`
 *   calls in parameter-context scripts (the element path, not the type);
 *   keys whose owning channel/framework is currently disabled
 *   in Settings (design C4a / task A5c). Reported back to the drafter /
 *   surfaced on the proposal card, but the proposal still proceeds.
 *
 * ## Key catalog
 *
 * The set of "known rule keys" is supplied by [RuleKeyRegistry] — that
 * registry combines the shared [RuleKeys], every registered channel's
 * [com.itangcent.easyapi.channel.spi.Channel.ruleKeys], and the implicit keys
 * read by name via `configReader.getFirst(…)`.
 *
 * Full duplicate-of-existing-rule detection needs live
 * `get_existing_rules_for_key` data and is out of scope for this v1 pass.
 */
object RuleProposalValidator : RuleValidator {

    /**
     * The keys whose values are single-line JSON objects, validated by
     * attempting a JSON parse. Mirrors the preamble's contract.
     */
    private val JSON_VALUE_KEYS = setOf(
        "method.additional.header",
        "method.additional.param",
        "method.additional.response.header",
        "json.additional.field",
    )

    /**
     * Valid filter prefixes inside `[...]`, mirroring the preamble's
     * "Valid filter prefixes (and ONLY these)" list.
     */
    private val VALID_FILTER_PREFIXES = setOf(
        "\$class:", "@", "#regex:", "#", "!", "groovy:"
    )

    private val CLASS_CONTEXT_SIMPLE_NAME =
        Regex("""(?:containingClass|defineClass)\(\)\s*\??\.\s*name\(\)""")

    /**
     * Groovy MOP probing of the context kind, e.g.
     * `it.respondsTo('containingClass')`. Functionally valid but fragile —
     * the built-in discriminator `it.contextType()` states the kind directly
     * (issue #756).
     */
    private val RESPONDS_TO_USAGE = Regex("""\brespondsTo\s*\(""")

    /**
     * `it.canonicalText()` on a **parameter** context returns the element
     * path (`com.example.Foo#bar.userId`), not the parameter's type — a
     * scalar-type check written with it returns true for every parameter.
     * Only warned for parameter-context keys.
     */
    private val PARAM_CANONICAL_TEXT = Regex("""\bit\.canonicalText\(\)""")

    /** Keys evaluated against parameter contexts (mirrors [RuleScriptContextCatalog]). */
    private fun isParamContextKey(key: String): Boolean =
        key.startsWith("param.") || key.startsWith("custom.param.") || key.startsWith("api.param.")

    /** Source kind for keys declared in [RuleKeys] (mirrors [RuleKeyRegistry]). */
    private const val SOURCE_GENERAL = "general"
    /** Source kind for keys read by name only (mirrors [RuleKeyRegistry]). */
    private const val SOURCE_IMPLICIT = "implicit"

    /**
     * Validate [content] as a rule file.
     *
     * Parsing is delegated to
     * [com.itangcent.easyapi.core.config.parser.ConfigTextParser] (the same
     * parser the config loader uses at export time), so this review sees the
     * exact [com.itangcent.easyapi.core.config.model.ConfigEntry] set that
     * would actually take effect — comments, directives (`###if`/`###include`),
     * and multi-line ```` ``` ```` blocks are all handled by the shared parser
     * rather than re-implemented here.
     *
     * @param project the current IntelliJ project. The known-key set is taken
     *     from [RuleKeyRegistry] (general + channel + implicit keys).
     */
    override suspend fun validate(content: String, project: Project): RuleValidation {
        val knownKeyNames = RuleKeyRegistry.getInstance(project).allKeyNames()
        // A5c: precompute key-name → disabled-source-id lookup (unfiltered
        // allKeys() view, mirroring findKey) so per-entry warnings are O(1).
        val disabledSourceByName: Map<String, String> = buildDisabledSourceMap(project)
        val entries = ConfigTextParser.getInstance(project).parse(content, "proposal")
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Pure-text soft warnings: these match against the raw source (not the
        // parsed entries) so they keep the exact in-block line number even for
        // multi-line ``` blocks, where ConfigEntry.lineNo points at the block's
        // opening line.
        for (lineNo in matchWarningLines(content, CLASS_CONTEXT_SIMPLE_NAME)) {
            warnings += "line $lineNo: class-context name() returns a simple class name; " +
                "use qualifiedName() for FQN/package comparisons."
        }
        for (lineNo in matchWarningLines(content, RESPONDS_TO_USAGE)) {
            warnings += "line $lineNo: respondsTo() guesses the context kind from the " +
                "method surface; use it.contextType() (returns 'class'/'method'/" +
                "'field'/'param') instead."
        }

        for (entry in entries) {
            val lineNo = entry.lineNo
            val key = entry.bareKey()
            val filter = entry.filter()

            // An empty `[]` filter is not valid syntax: the parser preserves
            // the whole key, and the filter extension reports null — surface it
            // explicitly rather than letting it pass as a bare key.
            if (filter == null && entry.key.contains('[') && entry.key.endsWith(']')) {
                errors += "line ${lineNo ?: "?"}: empty filter '[]' on key '$key' is not valid."
                continue
            }

            if (key !in knownKeyNames) {
                errors += "line ${lineNo ?: "?"}: unknown rule key '$key' (not in list_rule_keys)."
                continue
            }
            // A5c: soft warning when the key's owning channel/framework is
            // disabled in Settings (never blocks the proposal).
            disabledSourceByName[key]?.let { src ->
                warnings += "line ${lineNo ?: "?"}: key '$key' belongs to '$src', " +
                    "which is currently disabled in Settings."
            }
            if (filter != null) {
                val prefixIssue = checkFilterPrefix(filter)
                when (prefixIssue) {
                    is FilterIssue.Invalid ->
                        errors += "line ${lineNo ?: "?"}: invalid filter '$filter'. " +
                            "Valid prefixes: \$class:, @, #regex:, #<tag>, !, groovy:."
                    is FilterIssue.Deprecated ->
                        warnings += "line ${lineNo ?: "?"}: filter '$filter' uses the " +
                            "deprecated bare 'class:' form — prefer '\$class:'."
                    null -> Unit
                }
            }
            val value = entry.value
            if (key in JSON_VALUE_KEYS && value.isNotBlank()) {
                val v = value.trim()
                // A groovy value starts with `groovy:` and is script, not JSON.
                if (!v.startsWith("groovy:") && !isParsableJson(v)) {
                    errors += "line ${lineNo ?: "?"}: value for '$key' is not valid JSON " +
                        "(expected an object like {\"name\":\"…\",\"value\":\"…\"})."
                }
            }
            if (value.startsWith("groovy:")) {
                warnCanonicalTextOnParam(key, value, lineNo, warnings)
            }
        }
        return RuleValidation(errors = errors, warnings = warnings)
    }

    /**
     * Soft warning when a parameter-context rule's script calls
     * `it.canonicalText()` — the element path, not the parameter's type.
     */
    private fun warnCanonicalTextOnParam(key: String, script: String, lineNo: Int?, warnings: MutableList<String>) {
        if (!isParamContextKey(key)) return
        if (!PARAM_CANONICAL_TEXT.containsMatchIn(script)) return
        val where = lineNo?.let { "line $it: " } ?: ""
        warnings += "${where}canonicalText() on a parameter context returns the element " +
            "path (class#method.param), not the parameter's type — use type().name() for type checks."
    }

    /**
     * Maps [pattern] matches in [content] to 1-based line numbers, skipping
     * matches on comment lines. Shared by the semantic soft warnings.
     */
    private fun matchWarningLines(content: String, pattern: Regex): Set<Int> =
        pattern.findAll(content).mapNotNullTo(linkedSetOf()) { match ->
            val matchStart = match.range.first
            val lineStart = if (matchStart == 0) {
                0
            } else {
                content.lastIndexOf('\n', matchStart - 1) + 1
            }
            val lineEnd = content.indexOf('\n', matchStart).let { index ->
                if (index < 0) content.length else index
            }
            val sourceLine = content.substring(lineStart, lineEnd)
            if (sourceLine.trimStart().startsWith("#")) {
                null
            } else {
                content.substring(0, matchStart).count { it == '\n' } + 1
            }
        }

    private fun checkFilterPrefix(filter: String): FilterIssue? {
        if (filter.startsWith("class:")) return FilterIssue.Deprecated
        if (VALID_FILTER_PREFIXES.any { filter.startsWith(it) }) return null
        return FilterIssue.Invalid
    }

    private fun isParsableJson(text: String): Boolean = runCatching {
        GsonUtils.fromJson<Any>(text)
        true
    }.getOrDefault(false)

    /**
     * Builds a lookup from every known rule-key name (primary + aliases) to
     * the id of the disabled channel/framework that owns it (task A5c).
     * Returns an empty map when no owner is disabled.
     *
     * Mirrors the prefix-based ownership check in
     * [RuleKeyRegistry.isEnabledSource] so that a key hidden from
     * [RuleKeyRegistry.enabledKeys] (e.g. `postman.test` with Postman
     * disabled) is also surfaced as a soft warning here.
     */
    private fun buildDisabledSourceMap(project: Project): Map<String, String> {
        val registry = RuleKeyRegistry.getInstance(project)
        val channelRegistry = ChannelRegistry.getInstance(project)
        val allChannels = channelRegistry.allChannels()
        val frameworkRegistry = FrameworkRegistry.getInstance(project)
        val allRecognizers = CompositeApiClassRecognizer.getInstance(project).allRecognizers()

        val result = mutableMapOf<String, String>()
        for (info in registry.allKeys()) {
            val disabledSource = checkDisabledSource(
                info, allChannels, channelRegistry, allRecognizers, frameworkRegistry
            )
            if (disabledSource != null) {
                for (name in info.key.allNames) {
                    result[name] = disabledSource
                }
            }
        }
        return result
    }

    /**
     * Resolves the disabled owner (channel/framework id) for [info], or
     * `null` when the owner is enabled or the source kind is unknown.
     *
     * Resolution order (design C4a + AC-S4 prefix check):
     * 1. **Channel-sourced key** (`info.source` is a registered channel id):
     *    disabled iff that channel is disabled.
     * 2. **Framework-sourced key** (`info.source` is a registered framework
     *    name): disabled iff that framework is disabled.
     * 3. **General/implicit key**: disabled iff the key name prefix-matches a
     *    disabled channel (e.g. `postman.test` → `postman`). The `postman.*`
     *    keys are declared in [RuleKeys] (general source) but semantically
     *    owned by their channel — the prefix check mirrors
     *    [RuleKeyRegistry.isEnabledSource] so a key filtered out of
     *    `enabledKeys()` also warns here.
     * 4. **Unknown source kind**: never warn.
     */
    private fun checkDisabledSource(
        info: RuleKeyRegistry.RuleKeyInfo,
        allChannels: List<Channel>,
        channelRegistry: ChannelRegistry,
        allRecognizers: List<ApiClassRecognizer>,
        frameworkRegistry: FrameworkRegistry
    ): String? {
        val keyName = info.key.name
        val source = info.source

        // 1. Channel-sourced key.
        val sourceChannel = allChannels.firstOrNull { it.id == source }
        if (sourceChannel != null && !channelRegistry.isEnabled(sourceChannel)) {
            return source
        }

        // 2. Framework-sourced key.
        val sourceFramework = allRecognizers.firstOrNull { it.frameworkName == source }
        if (sourceFramework != null && !frameworkRegistry.isEnabled(sourceFramework)) {
            return source
        }

        // 3. General/implicit key: channel-prefix ownership (AC-S4).
        if (source == SOURCE_GENERAL || source == SOURCE_IMPLICIT) {
            for (channel in allChannels) {
                if (keyName.startsWith("${channel.id}.") && !channelRegistry.isEnabled(channel)) {
                    return channel.id
                }
            }
        }

        return null
    }

    private sealed class FilterIssue {
        object Invalid : FilterIssue()
        object Deprecated : FilterIssue()
    }
}
