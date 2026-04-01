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
            val options = resolveFromSeeText(seeTag.trim(), psiElement)
            if (!options.isNullOrEmpty()) return options
        }
        return null
    }

    /**
     * Resolve options from a single `@see` tag text.
     *
     * @param seeText the raw text after `@see`, e.g. `{@link com.example.UserType#type}`
     * @param context the PSI element where the tag appears (used for import resolution)
     */
    suspend fun resolveFromSeeText(
        seeText: String,
        context: PsiElement
    ): List<FieldOption>? {
        val parsed = linkResolver.parseLinkReference(seeText) ?: return null
        val psiClass = linkResolver.resolveClass(parsed.className, context) ?: return null

        return withContext(IdeDispatchers.ReadAction) {
            if (psiClass.isEnum) {
                resolveEnumOptions(psiClass, parsed.memberName)
            } else {
                resolveStaticOptions(psiClass)
            }
        }
    }

    /**
     * Resolve enum constants as options.
     * If [propertyName] is specified, uses that field's constructor argument as the value.
     * Otherwise uses the enum constant name.
     *
     * Supports both field names and getter method names:
     * - `type` → matches field `type` directly
     * - `getType` / `getType()` → converted to field `type`
     * - `isActive` / `isActive()` → converted to field `active`
     */
    private fun resolveEnumOptions(psiClass: PsiClass, propertyName: String?): List<FieldOption>? {
        val constants = psiClass.fields.filterIsInstance<PsiEnumConstant>()
        if (constants.isEmpty()) return null

        val resolvedFieldName = resolveFieldName(psiClass, propertyName)
        val instanceFields = psiClass.allFields
            .filter { it !is PsiEnumConstant && !it.hasModifierProperty(PsiModifier.STATIC) }
        val valueFieldIndex = if (resolvedFieldName != null) {
            instanceFields.indexOfFirst { it.name == resolvedFieldName }
        } else -1

        return constants.mapNotNull { constant ->
            val name = constant.name ?: return@mapNotNull null
            val desc = constant.docComment?.let { extractDocText(it) }

            val value: Any? = if (valueFieldIndex >= 0) {
                val args = constant.argumentList?.expressions
                if (args != null && valueFieldIndex < args.size) {
                    extractConstantValue(args[valueFieldIndex])
                } else name
            } else name

            FieldOption(value = value, desc = desc)
        }.ifEmpty { null }
    }

    /**
     * Resolve a property name that may be a getter method name to the actual field name.
     * Tries direct match first, then getter-to-property conversion.
     */
    private fun resolveFieldName(psiClass: PsiClass, propertyName: String?): String? {
        if (propertyName == null) return null

        val instanceFields = psiClass.allFields
            .filter { it !is PsiEnumConstant && !it.hasModifierProperty(PsiModifier.STATIC) }

        // Direct field name match
        if (instanceFields.any { it.name == propertyName }) {
            return propertyName
        }

        // Try getter-to-property conversion: getXxx → xxx, isXxx → xxx
        val derived = LinkResolver.getterToPropertyName(propertyName)
        if (derived != null && instanceFields.any { it.name == derived }) {
            return derived
        }

        return propertyName
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
