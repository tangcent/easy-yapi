package com.itangcent.easyapi.core.rule

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.util.json.GsonUtils
import com.itangcent.easyapi.core.util.text.KeyValueLineParser

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
 *   as the bare `class:` prefix. Reported back to the drafter / surfaced on
 *   the proposal card, but the proposal still proceeds.
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
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var inBlock = false
        content.lines().forEachIndexed { idx, raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachIndexed
            // Multi-line groovy value-block: skip the body (it is a free-form
            // script whose lines are not key=value).
            if (inBlock) {
                if (line == "```") inBlock = false
                return@forEachIndexed
            }
            // Plain comment (### is a directive, handled elsewhere; treat as
            // non-rule for this check).
            if (line.startsWith("#")) return@forEachIndexed
            val parsed = KeyValueLineParser.splitKeyFilterValue(line) ?: run {
                // Not a key=value line — skip (directives, stray text). We do
                // not error on every non-kv line to avoid false positives on
                // constructs the parser supports but this checker doesn't model.
                return@forEachIndexed
            }
            val (key, filter, value) = parsed
            val lineNo = idx + 1

            if (key !in knownKeyNames) {
                errors += "line $lineNo: unknown rule key '$key' (not in list_rule_keys)."
                return@forEachIndexed
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
