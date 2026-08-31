package com.itangcent.easyapi.core.rule

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer
import com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer
import com.itangcent.easyapi.core.util.json.GsonUtils
import com.itangcent.easyapi.core.util.text.KeyValueLineParser
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
 *   context kind instead of calling `it.contextType()`; keys whose owning
 *   channel/framework is currently disabled in Settings (design C4a / task
 *   A5c). Reported back to the drafter / surfaced on the proposal card, but
 *   the proposal still proceeds.
 *
 * ## Key catalog
 *
 * The set of "known rule keys" is supplied by [RuleKeyRegistry] when a
 * [Project] is available — that registry combines the shared [RuleKeys],
 * every registered channel's [com.itangcent.easyapi.channel.spi.Channel.ruleKeys],
 * and the implicit keys read by name via `configReader.getFirst(…)`. When
 * [validate] is called without a project (e.g. in lightweight unit tests),
 * the validator falls back to reflecting [RuleKeys] alone — channel-specific
 * and implicit keys are NOT recognized in that mode.
 *
 * Full duplicate-of-existing-rule detection needs live
 * `get_existing_rules_for_key` data and is out of scope for this v1 pass.
 */
object RuleProposalValidator {

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

    /** Source kind for keys declared in [RuleKeys] (mirrors [RuleKeyRegistry]). */
    private const val SOURCE_GENERAL = "general"
    /** Source kind for keys read by name only (mirrors [RuleKeyRegistry]). */
    private const val SOURCE_IMPLICIT = "implicit"

    /** Fallback known-key set used when no [Project] is supplied. */
    private val generalKeyNames: Set<String> by lazy { collectGeneralKeyNames() }

    /**
     * Validate [content] as a rule file.
     *
     * Comments (`#`), blank lines, and multi-line groovy value-blocks (delimited
     * by ```` ``` ````) are tolerated; every non-comment `key[filter]=value`
     * line is checked.
     *
     * @param project the current IntelliJ project. When non-null, the known-key
     *     set is taken from [RuleKeyRegistry] (general + channel + implicit
     *     keys). When null, only the shared [RuleKeys] are recognized — use
     *     the project form in production code so channel-specific keys
     *     (e.g. `hopp.prerequest`, `yapi.project`) are accepted.
     */
    fun validate(content: String, project: Project? = null): RuleValidation {
        val knownKeyNames = project
            ?.let { RuleKeyRegistry.getInstance(it).allKeyNames() }
            ?: generalKeyNames
        // A5c: precompute key-name → disabled-source-id lookup (unfiltered
        // allKeys() view, mirroring findKey) so per-line warnings are O(1).
        val disabledSourceByName: Map<String, String> =
            project?.let { buildDisabledSourceMap(it) } ?: emptyMap()
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val classContextSimpleNameWarningLines = matchWarningLines(content, CLASS_CONTEXT_SIMPLE_NAME)
        val respondsToWarningLines = matchWarningLines(content, RESPONDS_TO_USAGE)
        var inBlock = false
        content.lines().forEachIndexed { idx, raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachIndexed
            // Plain comment (### is a directive, handled elsewhere; treat as
            // non-rule for this check).
            if (line.startsWith("#")) return@forEachIndexed
            val lineNo = idx + 1
            if (lineNo in classContextSimpleNameWarningLines) {
                warnings += "line $lineNo: class-context name() returns a simple class name; " +
                    "use qualifiedName() for FQN/package comparisons."
            }
            if (lineNo in respondsToWarningLines) {
                warnings += "line $lineNo: respondsTo() guesses the context kind from the " +
                    "method surface; use it.contextType() (returns 'class'/'method'/" +
                    "'field'/'param') instead."
            }
            // Multi-line groovy value-block: scan each body line for semantic
            // warnings above, but skip structural key=value validation because
            // the body is a free-form script.
            if (inBlock) {
                if (line == "```") inBlock = false
                return@forEachIndexed
            }
            val parsed = KeyValueLineParser.splitKeyFilterValue(line) ?: run {
                // Not a key=value line — skip (directives, stray text). We do
                // not error on every non-kv line to avoid false positives on
                // constructs the parser supports but this checker doesn't model.
                return@forEachIndexed
            }
            val (key, filter, value) = parsed

            if (key !in knownKeyNames) {
                errors += "line $lineNo: unknown rule key '$key' (not in list_rule_keys)."
                return@forEachIndexed
            }
            // A5c: soft warning when the key's owning channel/framework is
            // disabled in Settings (never blocks the proposal).
            disabledSourceByName[key]?.let { src ->
                warnings += "line $lineNo: key '$key' belongs to '$src', " +
                    "which is currently disabled in Settings."
            }
            if (filter != null) {
                val prefixIssue = checkFilterPrefix(filter)
                when (prefixIssue) {
                    is FilterIssue.Invalid ->
                        errors += "line $lineNo: invalid filter '$filter'. " +
                            "Valid prefixes: \$class:, @, #regex:, #<tag>, !, groovy:."
                    is FilterIssue.Deprecated ->
                        warnings += "line $lineNo: filter '$filter' uses the " +
                            "deprecated bare 'class:' form — prefer '\$class:'."
                    null -> Unit
                }
            }
            if (key in JSON_VALUE_KEYS && value.isNotBlank()) {
                val v = value.trim()
                // Only validate inline single-line JSON here; a groovy
                // value-block opens with `` ` `` and is script, not JSON.
                if (!v.startsWith("groovy:") && !isParsableJson(v)) {
                    errors += "line $lineNo: value for '$key' is not valid JSON " +
                        "(expected an object like {\"name\":\"…\",\"value\":\"…\"})."
                }
            }
            if (value.trim() == "```" || value.trim().endsWith("```")) {
                inBlock = true
            }
        }
        return RuleValidation(errors = errors, warnings = warnings)
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

    private fun collectGeneralKeyNames(): Set<String> =
        RuleKey.collectFrom(RuleKeys).flatMap { it.allNames }.toSet()

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

/**
 * Result of [RuleProposalValidator.validate].
 *
 * @param errors hard failures that block the proposal from being staged.
 * @param warnings soft notes surfaced on the proposal card (never block).
 */
data class RuleValidation(
    val errors: List<String>,
    val warnings: List<String>
) {
    /** `true` when there are no blocking errors. */
    val ok: Boolean get() = errors.isEmpty()
}
