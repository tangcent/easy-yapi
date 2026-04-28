package com.itangcent.easyapi.rule.context

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNamedElement
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.psi.helper.BlockingAnnotationHelper
import com.itangcent.easyapi.psi.helper.BlockingDocHelper

/**
 * Base script context providing common operations for all PSI element types.
 *
 * Provides:
 * - Name and documentation access
 * - Annotation queries
 * - Modifier checks
 * - Source code access
 *
 * ## Usage in Scripts
 * ```
 * // Get element name
 * it.name()
 *
 * // Get documentation
 * it.doc()
 * it.doc("param") // Get specific tag
 *
 * // Check annotations
 * it.hasAnn("org.springframework.web.bind.annotation.RequestMapping")
 * it.ann("RequestMapping", "path") // Get annotation attribute
 *
 * // Check modifiers
 * it.hasModifier("static")
 * it.modifiers() // List all modifiers
 *
 * // Get source code
 * it.sourceCode()
 * it.defineCode() // Get declaration without body
 * ```
 *
 * @see ScriptPsiClassContext for class-specific operations
 * @see ScriptPsiMethodContext for method-specific operations
 * @see ScriptPsiFieldContext for field-specific operations
 */
open class ScriptItContext(protected val context: RuleContext) {

    private val docHelper = context.element?.let { BlockingDocHelper(context.docHelper) }
    private val annotationHelper = context.element?.let { BlockingAnnotationHelper(context.annotationHelper) }

    fun psi(): PsiElement? = context.element

    fun getName(): String? = readSync {
        (context.element as? PsiNamedElement)?.name
    }

    fun name(): String = getName() ?: ""

    fun doc(): String? {
        val el = context.element ?: return null
        return docHelper?.getAttrOfDocComment(el)
    }

    fun doc(tag: String): String? = docs(tag)?.firstOrNull()

    fun docs(tag: String): List<String>? {
        val el = context.element ?: return null
        return docHelper?.findDocsByTag(el, tag)
    }

    fun hasDoc(tag: String): Boolean = docs(tag) != null

    fun doc(tag: String, subTag: String): String? {
        val values = docs(tag) ?: return null
        for (v in values) {
            val trimmed = v.trim()
            if (trimmed.isEmpty()) continue
            val head = trimmed.substringBefore(" ").trim()
            if (head != subTag) continue
            return trimmed.substringAfter(" ", missingDelimiterValue = "").trim()
        }
        return null
    }

    fun hasModifier(modifier: String): Boolean = readSync {
        val owner = context.element as? PsiModifierListOwner ?: return@readSync false
        owner.hasModifierProperty(modifier)
    }

    fun modifiers(): List<String> = readSync {
        val owner = context.element as? PsiModifierListOwner ?: return@readSync emptyList()
        val candidates = listOf(
            PsiModifier.PUBLIC,
            PsiModifier.PROTECTED,
            PsiModifier.PRIVATE,
            PsiModifier.STATIC,
            PsiModifier.FINAL,
            PsiModifier.ABSTRACT,
            PsiModifier.SYNCHRONIZED,
            PsiModifier.NATIVE,
            PsiModifier.STRICTFP,
            PsiModifier.TRANSIENT,
            PsiModifier.VOLATILE,
        )
        candidates.filter { owner.hasModifierProperty(it) }
    }

    fun hasAnn(name: String): Boolean {
        val el = context.element ?: return false
        return annotationHelper?.hasAnn(el, name) ?: false
    }

    fun ann(name: String): String? = ann(name, "value")

    fun ann(name: String, attr: String): String? {
        val el = context.element ?: return null
        return annotationHelper?.findAttrAsString(el, name, attr)
    }

    fun annValue(name: String): Any? = annValue(name, "value")

    fun annValue(name: String, attr: String): Any? {
        val el = context.element ?: return null
        return annotationHelper?.findAttr(el, name, attr)
    }

    fun annMap(name: String): Map<String, Any?>? {
        val el = context.element ?: return null
        return annotationHelper?.findAnnMap(el, name)
    }

    fun annMaps(name: String): List<Map<String, Any?>>? {
        val el = context.element ?: return null
        return annotationHelper?.findAnnMaps(el, name)
    }

    open fun sourceCode(): String? = readSync { context.element?.text }

    open fun defineCode(): String? {
        val code = sourceCode()?.trim() ?: return null
        val brace = code.indexOf('{').takeIf { it >= 0 }
        val semi = code.indexOf(';').takeIf { it >= 0 }
        val cut = listOfNotNull(brace, semi).minOrNull()
        return (if (cut != null) code.substring(0, cut) else code).trim().ifEmpty { null }
    }

    open fun contextType(): String = "unknown"

    /**
     * Canonical text representation of this element.
     *
     * - class → qualified name (e.g., `com.itangcent.UserCtrl`)
     * - method → `class#method` (e.g., `com.itangcent.UserCtrl#greeting`)
     * - field → `class#field` (e.g., `com.itangcent.UserInfo#name`)
     * - param → `class#method.param` (e.g., `com.itangcent.UserCtrl#greeting.id`)
     * - type → qualified name with type args (e.g., `Mono<UserInfo>`)
     */
    open fun canonicalText(): String = name()

    /**
     * Get an extension value from the rule context.
     * Used by scripts to access context-specific data (e.g. request, response).
     */
    fun getExt(name: String): Any? = context.getExt(name)

    /**
     * Set an extension value on the rule context.
     */
    fun setExt(name: String, value: Any?) = context.setExt(name, value)

    override fun toString(): String = canonicalText()
}