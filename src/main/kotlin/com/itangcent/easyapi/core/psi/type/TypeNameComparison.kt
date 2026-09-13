package com.itangcent.easyapi.core.psi.type

/**
 * Coarse, spelling-independent comparison of two *declared type names* by "kind" —
 * the raw outer simple name, with primitives unified with their boxed forms.
 *
 * ## The one caller, and what it needs
 *
 * [com.itangcent.easyapi.core.psi.EnumValueResolver.findEnumFieldByType] — the Case-2
 * (`@see EnumClass` with no member) INTELLIGENT heuristic — must answer:
 *
 * > "Which instance field of this enum could be the value source for a referencing
 * > element declared as `<the reference's declared type>`?"
 *
 * Example: a DTO field `private int type` carrying `@see UserType` should select
 * `UserType.code` (declared `private final Integer code`). The difference on the two
 * sides is a *spelling* difference (`int` vs `Integer`), not a structural one: the
 * comparison runs on `PsiType.canonicalText`, which is spelled by the language and the
 * resolution state — Java gives `int` / `java.lang.Integer`, Kotlin gives `Int` / `Int?` /
 * `kotlin.Int`, and an unresolved reference degrades to a source-level simple name.
 *
 * ## Why the rules are permissive
 *
 * The result is a **candidate filter**, not a verdict. The caller narrows the enum's
 * instance fields with it, then breaks ties by (1) a field name equal to the reference's
 * name, (2) declaration order; when nothing matches it gives up and the enum falls back to
 * `name()`, which is the wrong encoding for enums whose value is a field
 * (see `EnumValueResolver`'s priority order). **A miss is expensive, a hit is cheap** —
 * so the comparison errs on the side of matching.
 *
 * ## What it does
 *
 * [isSameKind] compares [kindOf] of both sides:
 * - drops qualification — `java.lang.String` ≡ `kotlin.String` ≡ `String`
 * - drops a trailing `?` — `Int?` ≡ `Int`
 * - unifies primitive/boxed forms **case-insensitively**, the one place Java and Kotlin
 *   differ by case alone — `int` ≡ `Integer` ≡ `java.lang.Integer` ≡ `Int` ≡ `kotlin.Int`
 * - keeps the case of everything else — `String` ≢ `string`
 * - keeps array-ness (`String[]` ≢ `String`, `int[]` ≡ `Int[]`) but does **not** bridge
 *   Kotlin's `Array<X>` / `IntArray` to their Java spellings
 *
 * ## What it deliberately ignores
 *
 * **Type arguments.** `List<Int>`, `List<String>`, `List<? extends Integer>` and a raw
 * `java.util.List` are all the same kind. Arguments are spelled inconsistently across
 * Java, Kotlin and unresolved PSI (raw types, wildcards, star projections), so comparing
 * them would produce misses on exactly the candidates that must not be lost; when the
 * arguments are the real discriminator, the caller's field-name tie-break recovers the
 * right field.
 *
 * ## Non-goals — do not reuse this for anything else
 *
 * This is **not** a type-equality, assignability or subtyping check, and **not** a
 * canonicalization routine:
 * - no subtyping — `List<X>` ≢ `Collection<X>`, nothing ≡ `Object`
 * - no numeric widening — `byte` ≢ `Integer` (JSON-type reconciliation is done separately,
 *   by `EnumValueResolver.resolveJsonType` / `reconcileType`)
 * - raw `List` ≡ `List<X>` holds *by construction*, not by erasure semantics
 *
 * Accepted false positive: a user class named like a boxed primitive
 * (`com.acme.Integer`) is the same kind as `int`.
 *
 * If a future caller needs real type relations, resolve the `PsiType`s and ask
 * `PsiType.isAssignableFrom` — do not grow rules here.
 *
 * Pure code — callable on any thread, performs no PSI access.
 */
internal object TypeNameComparison {

    /** Simple name (lowercased) → canonical boxed name; the shared primitive-family table. */
    private val BOXED_BY_SIMPLE_NAME = PrimitiveFamilies.BOXED_BY_SIMPLE_NAME

    /**
     * Whether two declared type names are the **same kind**.
     *
     * See the object KDoc for the exact rules — in particular it is *not* an equality or
     * subtyping check, and type arguments do not participate.
     *
     * @param fieldType one declared type, e.g. an enum instance field's `PsiType.canonicalText`
     * @param targetType the other, e.g. the referencing element's `PsiType.canonicalText`
     */
    fun isSameKind(fieldType: String, targetType: String): Boolean =
        kindOf(fieldType) == kindOf(targetType)

    /**
     * The comparison key: the raw outer simple name of [type] (boxed for primitives),
     * with type arguments, a trailing `?` and qualification discarded.
     *
     * Exposed for tests and diagnostics — callers should use [isSameKind]. **Not** a
     * canonicalization routine: it intentionally throws type arguments away, so the
     * result must never be used as a type name.
     *
     * @param type a declared-type spelling such as `PsiType.canonicalText`; any other
     *   text is passed through unchanged apart from the stripping described above
     */
    fun kindOf(type: String): String {
        val raw = type.trim().substringBefore('<').trim().removeSuffix("?").trim()
        val base = raw.substringBefore('[').trim()
        val arraySuffix = raw.substringAfter(base, "")
        val simple = base.substringAfterLast('.').trim()
        return (BOXED_BY_SIMPLE_NAME[simple.lowercase()] ?: simple) + arraySuffix
    }
}
