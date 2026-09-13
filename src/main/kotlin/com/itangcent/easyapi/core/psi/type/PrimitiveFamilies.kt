package com.itangcent.easyapi.core.psi.type

/**
 * The single source of truth for the Java primitive *families*.
 *
 * A primitive is spelled in several ways depending on where the text comes from — source
 * code, `PsiType.canonicalText`, an unresolved/light-PSI reference, or Kotlin:
 * the primitive keyword (`int`), the boxed class (`java.lang.Integer`), and the lowercased
 * simple name (`integer`, `character`).
 *
 * Those same facts used to be re-encoded in four independent tables — [SpecialTypeHandler]'s
 * wrapper/keyword maps, [TypeNameComparison]'s boxed-name map and
 * `TypeResolver.resolvePrimitiveKind` — which is exactly the kind of duplication that drifts:
 * one table says `integer` is an alias, the next does not; one knows `void`, the next does not.
 * They now all derive from [families], so adding or renaming a primitive is a one-line change.
 *
 * ## `void` is a family, but not a wrapper
 *
 * `void` participates so that `TypeResolver.resolveFromCanonicalText("void")` keeps resolving
 * to [PrimitiveKind.VOID], but it is deliberately **excluded** from every wrapper-derived table:
 * `java.lang.Void` is not a boxed wrapper here (`SpecialTypeHandler.isPrimitiveWrapper("java.lang.Void")`
 * must stay `false`, otherwise a `Void` field would start resolving as a primitive — a behaviour
 * change nothing asked for).
 *
 * Pure — no PSI access; callable on any thread.
 */
internal object PrimitiveFamilies {

    private class Family(
        val kind: PrimitiveKind,
        val keyword: String,
        val wrapperFqn: String,
        /** Extra lowercased simple names accepted for this family. */
        val aliases: List<String> = emptyList()
    )

    private val families = listOf(
        Family(PrimitiveKind.BOOLEAN, "boolean", "java.lang.Boolean"),
        Family(PrimitiveKind.BYTE, "byte", "java.lang.Byte"),
        Family(PrimitiveKind.CHAR, "char", "java.lang.Character", aliases = listOf("character")),
        Family(PrimitiveKind.SHORT, "short", "java.lang.Short"),
        Family(PrimitiveKind.INT, "int", "java.lang.Integer", aliases = listOf("integer")),
        Family(PrimitiveKind.LONG, "long", "java.lang.Long"),
        Family(PrimitiveKind.FLOAT, "float", "java.lang.Float"),
        Family(PrimitiveKind.DOUBLE, "double", "java.lang.Double"),
        Family(PrimitiveKind.VOID, "void", "java.lang.Void")
    )

    private val valueFamilies = families.filter { it.kind != PrimitiveKind.VOID }

    /** The primitive keywords (`boolean`, `byte`, `char`, `short`, `int`, `long`, `float`, `double`). */
    val PRIMITIVE_KEYWORDS: Set<String> = valueFamilies.mapTo(LinkedHashSet()) { it.keyword }

    /** Boxed FQN → primitive keyword (`java.lang.Integer` → `int`). Excludes `java.lang.Void`. */
    val WRAPPER_TO_KEYWORD: Map<String, String> =
        valueFamilies.associate { it.wrapperFqn to it.keyword }

    /** Lowercased simple name → canonical boxed FQN (`int`/`integer` → `java.lang.Integer`). */
    val BOXED_BY_SIMPLE_NAME: Map<String, String> = buildMap {
        valueFamilies.forEach { family ->
            put(family.keyword, family.wrapperFqn)
            family.aliases.forEach { put(it, family.wrapperFqn) }
        }
    }

    /** Any accepted spelling — keyword or boxed FQN — → primitive kind. Includes `void`. */
    val KIND_BY_SPELLING: Map<String, PrimitiveKind> = buildMap {
        families.forEach { family ->
            put(family.keyword, family.kind)
            put(family.wrapperFqn, family.kind)
        }
    }

    /** Whether [spelling] is a boxed wrapper FQN such as `java.lang.Integer`. `java.lang.Void` is not. */
    fun isWrapperFqn(spelling: String): Boolean = spelling in WRAPPER_TO_KEYWORD
}
