package com.itangcent.easyapi.psi

import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.model.FieldOption
import kotlinx.coroutines.withContext

/**
 * Resolves `@see` tags in field doc comments to extract enum/static constant options.
 *
 * ## Supported `@see` tag patterns
 *
 * ### Plain reference (no wrapper)
 * ```
 * @see Xxx                                          → class only
 * @see xxx.xxx.Xxx                                  → fully-qualified class
 * @see Xxx#field                                    → class + field (hash)
 * @see xxx.xxx.Xxx#field                            → fully-qualified class + field (hash)
 * @see Xxx.field                                    → class + field (dot, lowercase)
 * @see xxx.xxx.Xxx.field                            → fully-qualified class + field (dot)
 * @see Xxx#getField()                               → class + getter method (→ field)
 * @see xxx.xxx.Xxx#getField()                       → fully-qualified class + getter
 * @see Xxx#isActive()                               → class + boolean getter (→ active)
 * ```
 *
 * ### Java `{@link ...}` wrapper
 * ```
 * @see {@link Xxx}                                  → class only
 * @see {@link xxx.xxx.Xxx}                          → fully-qualified class
 * @see {@link Xxx#field}                            → class + field (hash)
 * @see {@link xxx.xxx.Xxx#field}                    → fully-qualified class + field (hash)
 * @see {@link Xxx.field}                            → class + field (dot, lowercase)
 * @see {@link Xxx#getField()}                       → class + getter method (→ field)
 * @see {@link xxx.xxx.Xxx#getField()}               → fully-qualified class + getter
 * ```
 *
 * ### Kotlin KDoc `[...]` wrapper
 * ```
 * @see [Xxx]                                        → class only
 * @see [xxx.xxx.Xxx]                                → fully-qualified class
 * @see [Xxx.field]                                  → class + field (dot, lowercase)
 * @see [xxx.xxx.Xxx.field]                          → fully-qualified class + field (dot)
 * @see [Xxx.getField()]                             → class + getter method (→ field)
 * @see [xxx.xxx.Xxx.getField()]                     → fully-qualified class + getter
 * @see [Xxx.isActive()]                             → class + boolean getter (→ active)
 * ```
 *
 * ## Resolution behavior
 * - When the referenced class is an enum, extracts its constants as [FieldOption]s.
 * - When a property/getter is specified (e.g., `UserType#type` or `UserType#getType()`),
 *   uses that field's constructor argument value instead of the enum constant name.
 *   Getter method names (`getXxx`, `isXxx`) are automatically converted to field names.
 * - For non-enum classes, extracts `static final` fields as options.
 * - Multiple `@see` tags on a single field are supported; the first one that resolves wins.
 * - Class resolution tries: fully-qualified name → same package → imports.
 */
class SeeTagResolver(
    private val project: com.intellij.openapi.project.Project
) {

    private val linkResolver: LinkResolver by lazy { LinkResolver.getInstance(project) }

    private val enumValueResolver: EnumValueResolver by lazy { EnumValueResolver.getInstance(project) }

    /**
     * Resolve options from all `@see` tags on the given PSI element.
     * Returns options from the first `@see` tag that successfully resolves, or null.
     */
    suspend fun resolveOptions(
        psiElement: PsiElement,
        docHelper: DocHelper
    ): List<FieldOption>? {
        val seeTags = docHelper.findDocsByTag(psiElement, "see") ?: return null
        for (seeTag in seeTags) {
            val options = resolveFromSeeText(seeTag.trim(), psiElement, docHelper)
            if (!options.isNullOrEmpty()) return options
        }
        return null
    }

    /**
     * Resolve options from all `@see` tags on the given PSI element, along with
     * the resolved enum's value-field JSON type (for Case 2 type reconciliation).
     *
     * The `valueFieldJsonType` is non-null only when the `@see` target is an enum;
     * it is null for static-constant classes or when no `@see` tag resolves.
     * Callers use it with [EnumValueResolver.reconcileType] to adjust the
     * declared field type against the actual enum value type (Req 7, D-TYPE).
     */
    suspend fun resolveOptionsWithType(
        psiElement: PsiElement,
        docHelper: DocHelper
    ): ResolvedSeeOptions? {
        val seeTags = docHelper.findDocsByTag(psiElement, "see") ?: return null
        for (seeTag in seeTags) {
            val resolved = resolveFromSeeTextWithType(seeTag.trim(), psiElement, docHelper)
            if (resolved != null) return resolved
        }
        return null
    }

    /**
     * Result of resolving a `@see` tag: the options plus the resolved enum's
     * value-field JSON type (null for non-enum / static-constant targets).
     */
    data class ResolvedSeeOptions(
        val options: List<FieldOption>,
        val valueFieldJsonType: String?
    )

    /**
     * Resolve options from a single `@see` tag text.
     *
     * @param seeText the raw text after `@see`, e.g. `{@link com.example.UserType#type}`
     * @param context the PSI element where the tag appears (used for import resolution and type matching)
     * @param docHelper the [DocHelper] for doc-comment extraction (Req 6.2 — unifies Case 1/Case 2)
     */
    suspend fun resolveFromSeeText(
        seeText: String,
        context: PsiElement,
        docHelper: DocHelper
    ): List<FieldOption>? =
        resolveFromSeeTextWithType(seeText, context, docHelper)?.options

    /**
     * Resolve options from a single `@see` tag text, along with the resolved
     * enum's value-field JSON type (for Case 2 type reconciliation).
     *
     * @param seeText the raw text after `@see`, e.g. `{@link com.example.UserType#type}`
     * @param context the PSI element where the tag appears (used for import resolution and type matching)
     * @param docHelper the [DocHelper] for doc-comment extraction (Req 6.2 — unifies Case 1/Case 2)
     * @return [ResolvedSeeOptions] with options and the enum's value-field JSON type
     *         (null for non-enum targets or when resolution fails)
     */
    suspend fun resolveFromSeeTextWithType(
        seeText: String,
        context: PsiElement,
        docHelper: DocHelper
    ): ResolvedSeeOptions? {
        val parsed = linkResolver.parseLinkReference(seeText) ?: return null

        return withContext(IdeDispatchers.ReadAction) {
            // resolveClass accesses PSI (imports, containingFile) and must be
            // inside a read action — otherwise it can intermittently return null
            // when the PSI index is not ready, causing NPEs in callers.
            val psiClass = linkResolver.resolveClass(parsed.className, context) ?: return@withContext null

            if (psiClass.isEnum) {
                // Delegate to EnumValueResolver (D-CENTRAL).
                // `parsed.memberName` carries the preserved `()` from `parseLinkReference`
                // so the resolver can distinguish `name()` (pseudo-field) from `name`
                // (instance field) — issue #1383.
                val resolution = enumValueResolver.resolve(
                    enumClass = psiClass,
                    context = context,
                    seeMemberName = parsed.memberName
                )
                val options = enumValueResolver.buildOptions(psiClass, resolution, docHelper)
                val jsonType = enumValueResolver.resolveJsonType(psiClass, resolution)
                if (options != null) ResolvedSeeOptions(options, jsonType) else null
            } else {
                val staticOptions = resolveStaticOptions(psiClass)
                if (staticOptions != null) ResolvedSeeOptions(staticOptions, null) else null
            }
        }
    }

    /**
     * Resolve static final fields as options (for non-enum constant classes).
     */
    private fun resolveStaticOptions(psiClass: PsiClass): List<FieldOption>? {
        val staticFields = psiClass.allFields.filter {
            it.hasModifierProperty(PsiModifier.STATIC) && it.hasModifierProperty(PsiModifier.FINAL)
        }
        if (staticFields.isEmpty()) return null

        return staticFields.mapNotNull { field ->
            val name = field.name ?: return@mapNotNull null
            val value = extractConstantValue(field.initializer) ?: name
            val desc = field.docComment?.let { extractDocText(it) }
            FieldOption(value = value, desc = desc ?: name)
        }.ifEmpty { null }
    }

    private fun extractDocText(docComment: PsiDocComment): String? {
        return docComment.descriptionElements
            .joinToString("") { it.text.trimEnd() }
            .trim()
            .lines()
            .joinToString("\n") { it.removePrefix(" ").trimEnd() }
            .trim()
            .takeIf { it.isNotBlank() }
    }

    companion object {
        fun extractConstantValue(expression: PsiExpression?): Any? {
            return when (expression) {
                is PsiLiteralExpression -> expression.value
                is PsiPrefixExpression -> {
                    val value = extractConstantValue(expression.operand)
                    if (expression.operationSign.text == "-" && value is Number) {
                        when (value) {
                            is Int -> -value
                            is Long -> -value
                            is Double -> -value
                            is Float -> -value
                            else -> value
                        }
                    } else value
                }

                is PsiReferenceExpression -> {
                    val resolved = expression.resolve()
                    if (resolved is PsiField &&
                        resolved.hasModifierProperty(PsiModifier.STATIC) &&
                        resolved.hasModifierProperty(PsiModifier.FINAL)
                    ) {
                        extractConstantValue(resolved.initializer)
                    } else null
                }

                else -> null
            }
        }
    }
}
