package com.itangcent.easyapi.core.rule

import com.itangcent.easyapi.core.rule.context.ClassContext
import com.itangcent.easyapi.core.rule.context.FieldContext
import com.itangcent.easyapi.core.rule.context.ItContext
import com.itangcent.easyapi.core.rule.context.MethodContext
import com.itangcent.easyapi.core.rule.context.ParameterContext
import kotlin.reflect.KClass

/**
 * Self-describing contract of a [RuleKey]: how it is evaluated (scheme),
 * what input it expects, and what output it is expected to produce.
 *
 * This is the single source of truth for the *semantics* of a rule key that
 * were previously scattered across `RuleScriptContextCatalog` (a long chain
 * of name-prefix / set-membership `when` branches) and re-derived ad hoc by
 * [RuleProposalValidator] and [RuleDryRunValidator]. With the scheme attached
 * to the [RuleKey] itself, a new key declares its behaviour once, at the point
 * of declaration, and every consumer — the validators, the AI tooling, and the
 * dry-run engine — reads it back instead of guessing from the name.
 *
 * Every field is optional on purpose. Existing keys adopt the scheme
 * incrementally; a [RuleKey] with an empty scheme keeps falling back to the
 * legacy name-based resolution until it is migrated.
 *
 * ## The three concerns
 *
 * 1. **Output format** — [outputShape] and [summary] declare *what* the value
 *    is expected to produce. The scheme does **not** declare *how* the value is
 *    evaluated: the rule engine decides that dynamically by the value's shape —
 *    a `groovy:`-prefixed value runs as a Groovy rule, any other value is
 *    handled by the matching [RuleParser] (literal text, `#regex:` pattern,
 *    `@` annotation, `$class:` type match, …). Every key accepts Groovy,
 *    literal, or any RuleParser-supported form; there is no per-key execution
 *    mode.
 * 2. **Expected input** — [contextKinds] is the declarative form of the old
 *    `itKindsFor` / `dryRunContextKinds` mapping: the PSI context kinds a
 *    script may be evaluated against. [additionalBindings] declares the extra
 *    runtime variables beyond `it` and the common helpers (`api`, `request`,
 *    `response`, `collection`, `item`, `document`, `a`/`b` …), each with an
 *    optional [RuleBinding.kind] that declares *what* is injected — a script
 *    object id, or a [ContextKind] id for a plain context injection (e.g.
 *    `a`/`b` are `field`/`method` members).
 * 3. **Expected output** — [outputShape] and [summary] tell the AI what the
 *    rule is supposed to produce and when to use it. [jsonValue] and
 *    [dryRunnable] drive validation: a `jsonValue` key whose value is not
 *    Groovy must parse as JSON; a non-[dryRunnable] key is skipped by the
 *    dry-run pass.
 *
 * @see RuleKey.scheme
 * @see RuleKeys for the migrated, self-describing key declarations
 */
data class RuleKeyScheme(
    /** Human-readable one-liner: when to use this key / what it decides. */
    val summary: String? = null,

    /** The PSI context kinds the rule is evaluated against. */
    val contextKinds: List<ContextKind> = emptyList(),

    /** Extra runtime bindings beyond `it` and the common helpers, each with an
     *  optional declared [RuleBinding.kind]. */
    val additionalBindings: List<RuleBinding> = emptyList(),

    /** The expected shape of the produced value. */
    val outputShape: OutputShape? = null,

    /** True when the value is read as static config and never scripted. */
    val staticConfiguration: Boolean = false,

    /** False when a Groovy value/filter for this key cannot be dry-run safely. */
    val dryRunnable: Boolean = true,

    /** True when a plain (non-Groovy) value must be a valid JSON object. */
    val jsonValue: Boolean = false,

    /** Extra guidance surfaced to the AI (e.g. discriminator hints). */
    val notes: List<String> = emptyList()
) {
    /**
     * Convenience: the context-kind ids in the runtime order a dry-run
     * should evaluate against.
     */
    val dryRunKindIds: List<String>
        get() = contextKinds.map(ContextKind::id)

    /** Convenience: whether the value is ever evaluated as a script. */
    val isScripted: Boolean get() = !staticConfiguration
}

/**
 * The PSI context kinds a rule value may be evaluated against. This is the
 * declarative form of the former `itKindsFor` name matching.
 */
enum class ContextKind(val id: String, val typeClass: KClass<*>, val description: String) {
    /** No PSI element; only common helper bindings are present. */
    EMPTY("empty", ItContext::class, "No PSI element is supplied; common helper bindings remain available."),

    /** A PSI class context. */
    CLASS("class", ClassContext::class, "PSI class context."),

    /** A PSI method context. */
    METHOD("method", MethodContext::class, "PSI method context."),

    /** A PSI field (or object-model member) context. */
    FIELD("field", FieldContext::class, "PSI field context; object-model fields may also be represented by a method context."),

    /** A PSI parameter context. */
    PARAMETER("parameter", ParameterContext::class, "PSI parameter context.")
}

/**
 * A runtime binding the rule key injects beyond `it` and the common helpers.
 *
 * Two distinct kinds are possible, distinguished by [kind]:
 *
 * - **Script object** — a shared object with a callable API (e.g. `request`,
 *   `response`, `api`, `collection`). [kind] is the object id, and the
 *   profiler reflects the wrapper class to surface its method signatures.
 *   These appear in a stage's `objectRefs`.
 * - **Context injection** — a plain value/context variable injected for the
 *   closure (e.g. the `a`/`b` members of `field.order.with`). [kind] is a
 *   [ContextKind] id (`"field"`, `"method"`, …) describing *what type of
 *   value* is injected. It has no script-object API, so it is listed as a
 *   binding (with its type) but never emitted into `objectRefs`.
 *
 * Declaring the injection type on the scheme itself — instead of guessing it
 * from a bare name — is what lets the profiler tell the two apart without a
 * hard-coded `a`/`b` special case.
 *
 * @param name the binding variable name
 * @param kind the injected entity id. For a script object this is the object
 *     id (which usually equals [name]); for a context injection it is the
 *     [ContextKind] id (e.g. `"field"`). Defaults to [name] — the common case
 *     where the injected entity is the thing named by [name] itself. Only
 *     override it (e.g. `binding("a", ContextKind.FIELD)`) when the variable
 *     name and the injected type differ.
 */
data class RuleBinding(
    val name: String,
    val kind: String = name
)

/**
 * Convenience factory for declaring an [additionalBindings] entry by name,
 * where the injected entity is the thing named by [name] itself (the common
 * case for script objects like `request`, `api`, `collection`).
 */
fun binding(name: String): RuleBinding = RuleBinding(name)

/**
 * Convenience factory for declaring a context injection with an explicit
 * [ContextKind] type, e.g. `binding("a", ContextKind.FIELD)` — used when the
 * variable name and the injected type differ.
 */
fun binding(name: String, kind: ContextKind): RuleBinding = RuleBinding(name, kind.id)

/**
 * Convenience factory for declaring several same-type bindings at once,
 * e.g. `bindings("a", "b")` (both named by their own injected type).
 */
fun bindings(vararg names: String): List<RuleBinding> = names.map(::binding)

/**
 * The expected shape of the value a rule produces. Kept coarse on purpose —
 * enough to steer the AI, not a full type system.
 */
enum class OutputShape {
    /** A single scalar string. */
    STRING,

    /** Multiple strings merged into one. */
    MERGED_STRING,

    /** A boolean decision. */
    BOOLEAN,

    /** An integer. */
    INT,

    /** A side-effect event producing no value. */
    EVENT,

    /** A JSON object (single-line, parsed for validation). */
    JSON
}
