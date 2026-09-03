package com.itangcent.easyapi.core.rule.context

/**
 * A key-specific script-context contract returned to the AI tooling as
 * structured JSON. Built from a [com.itangcent.easyapi.core.rule.RuleKey]'s
 * self-describing [com.itangcent.easyapi.core.rule.RuleKeyScheme] plus
 * reflection over the runtime script wrappers.
 *
 * Every non-static key shares one evaluation path, so the contract is flat:
 * the bindings and object references that evaluation provides. (The former
 * `stages` layer — always exactly one "rule-evaluation" stage with three
 * constant fields — was a leftover of the removed two-stage model and has
 * been collapsed into these top-level fields.)
 */
data class RuleScriptProfile(
    val key: String,
    val aliases: List<String>,
    val source: String,
    val executionMode: String,
    val description: String,
    /** Every binding the evaluation provides: `it`, the common helpers, and the key's additional bindings. Empty for static-configuration keys. */
    val bindings: List<ScriptBinding>,
    /** Ids of the shared script objects this key makes callable — join against the object dictionary ([RuleKeyScriptProfiler.collectAllObjects]). */
    val objectRefs: List<String>,
    val notes: List<String>
) {
    /** The context kinds `it` can take for this key (e.g. ["class", "method"]). */
    val itContexts: List<String>
        get() = bindings.firstOrNull { it.name == "it" }?.objectTypes.orEmpty()
}

/**
 * A script variable the evaluation binds (it, logger, session, request, …)
 * and the object ids it points at.
 */
data class ScriptBinding(
    val name: String,
    val aliases: List<String> = emptyList(),
    val objectTypes: List<String>,
    val availability: String,
    val description: String
)

/** Public, Groovy-callable API for a script object — methods only. */
data class ScriptObjectApi(
    val id: String,
    val type: String,
    val description: String,
    val methods: List<ScriptMethodApi>
)

/** A callable script method and its runtime-visible signature. */
data class ScriptMethodApi(
    val name: String,
    val returns: String,
    val parameters: List<ScriptParameterApi>
)

/**
 * One method parameter in [ScriptMethodApi].
 *
 * There is deliberately no `optional` flag: Kotlin default arguments are a
 * compile-time Kotlin feature invisible to Groovy's MOP, so from a rule
 * script every listed parameter must be supplied. Vararg-ness is real at
 * runtime and kept.
 */
data class ScriptParameterApi(
    val name: String,
    val type: String,
    val vararg: Boolean
)
