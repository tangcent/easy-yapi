package com.itangcent.easyapi.psi.helper

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.psi.adapter.PsiLanguageAdapter
import com.itangcent.easyapi.psi.adapter.PsiLanguageAdapterLoader

/**
 * Language-aware implementation of [DocHelper] that supports multiple JVM languages.
 *
 * Delegates documentation extraction to language-specific [PsiLanguageAdapter] instances
 * loaded via [PsiLanguageAdapterLoader]. When a language adapter is available for the
 * element's language (e.g., Java, Kotlin, Scala, Groovy), it uses that adapter's native
 * doc comment resolution (Javadoc, KDoc, Scaladoc, etc.).
 *
 * ## Resolution Strategy
 * For each method:
 * 1. Find a [PsiLanguageAdapter] that [supports][PsiLanguageAdapter.supportsElement] the element
 * 2. Use the adapter's [PsiLanguageAdapter.resolveDocComment] to extract
 *    language-native documentation
 * 3. For operations requiring PSI-level access (descriptions, EOL comments),
 *    implement directly using PSI APIs
 *
 * ## Thread Safety
 * All suspend functions wrap PSI access in [read] blocks to ensure
 * [ReadAction](https://plugins.jetbrains.com/docs/intellij/threading-model.html) compliance.
 *
 * ## Service Registration
 * Registered as a [project-level service][Service.Level.PROJECT] so it can be
 * obtained via [getInstance] or `project.service<UnifiedDocHelper>()`.
 *
 * @see DocHelper for the interface contract
 * @see PsiLanguageAdapter for language-specific doc resolution
 * @see PsiLanguageAdapterLoader for adapter discovery
 */
@Service(Service.Level.PROJECT)
class UnifiedDocHelper : DocHelper {

    companion object {
        private const val COMMENT_PREFIX = "//"
        private val DOC_COMMENT_PREFIXES = listOf("*", "///", "//")

        /**
         * Returns the [UnifiedDocHelper] instance for the given [project].
         */
        fun getInstance(project: Project): UnifiedDocHelper = project.service()
    }

    /**
     * Lazily loaded list of available language adapters.
     *
     * Adapters for unavailable language plugins are automatically excluded
     * by [PsiLanguageAdapterLoader].
     */
    private val adapters: List<PsiLanguageAdapter> by lazy {
        PsiLanguageAdapterLoader.loadAdapters()
    }

    override suspend fun hasTag(psiElement: PsiElement?, tag: String?): Boolean {
        if (psiElement == null || tag == null) return false
        return read {
            val adapter = findAdapter(psiElement)
            adapter?.resolveDocComment(psiElement)?.tags?.any { it.name == tag } == true
        }
    }

    override suspend fun findDocByTag(psiElement: PsiElement?, tag: String?): String? {
        if (psiElement == null || tag == null) return null
        return read {
            val adapter = findAdapter(psiElement)
            adapter?.resolveDocComment(psiElement)
                ?.tags?.firstOrNull { it.name == tag }?.value
        }
    }

    override suspend fun findDocsByTag(psiElement: PsiElement?, tag: String?): List<String>? {
        if (psiElement == null || tag == null) return null
        return read {
            val adapter = findAdapter(psiElement)
            val tags = adapter?.resolveDocComment(psiElement)
                ?.tags?.filter { it.name == tag }?.map { it.value }
            tags?.takeIf { it.isNotEmpty() }
        }
    }

    override suspend fun findDocsByTagAndName(psiElement: PsiElement?, tag: String, name: String): String? {
        if (psiElement == null) return null
        return read {
            val adapter = findAdapter(psiElement)
            adapter?.resolveDocComment(psiElement)
                ?.tags?.firstOrNull { docTag ->
                    docTag.name == tag && (
                        docTag.value == name ||
                        docTag.value.startsWith("$name ") ||
                        docTag.value.startsWith("$name\t")
                    )
                }
                ?.value?.removePrefix(name)?.trim()
        }
    }

    override suspend fun getAttrOfDocComment(psiElement: PsiElement?): String? {
        return read {
            val owner = psiElement as? PsiDocCommentOwner ?: return@read null
            val docComment = owner.docComment ?: return@read null
            getDocCommentContent(docComment)
        }
    }

    override fun getDocCommentContent(docComment: PsiDocComment): String? {
        val descriptions = docComment.descriptionElements
        val text = descriptions.joinToString(separator = "") { desc -> desc.tinyText() }.trim()
        val lines = text.lines()
        return lines.asSequence()
            .map { it.removePrefix(" ").trimEnd() }
            .joinToString(separator = "\n")
            .trim()
    }

    override suspend fun getSubTagMapOfDocComment(psiElement: PsiElement?, tag: String): Map<String, String?> {
        if (psiElement == null) return emptyMap()
        return read {
            val adapter = findAdapter(psiElement)
            adapter?.resolveDocComment(psiElement)
                ?.tags?.filter { it.name == tag }
                ?.associate {
                    val firstWord = it.value.substringBefore(" ")
                    val rest = it.value.substringAfter(" ", "").trim()
                    firstWord to rest.ifEmpty { null }
                }
                ?: emptyMap()
        }
    }

    override suspend fun getTagMapOfDocComment(psiElement: PsiElement?): Map<String, String?> {
        if (psiElement == null) return emptyMap()
        return read {
            val adapter = findAdapter(psiElement)
            adapter?.resolveDocComment(psiElement)
                ?.tags?.associate { it.name to it.value }
                ?: emptyMap()
        }
    }

    override fun getEolComment(psiElement: PsiElement): String? {
        val text = psiElement.text ?: return null

        if (text.contains(COMMENT_PREFIX)) {
            return psiElement.children
                .asSequence()
                .findEolComment { false }
        }

        psiElement.nextSiblings()
            .findEolComment()
            ?.let {
                return it
            }

        return null
    }

    override suspend fun getAttrOfField(field: PsiField): String? {
        val attrInDoc = getAttrOfDocComment(field)
        val eolComment = getEolComment(field)?.takeIf { it != attrInDoc }
        return listOfNotNull(attrInDoc, eolComment)
            .distinct()
            .joinToString("\n")
            .ifEmpty { null }
    }

    /**
     * Finds the first language adapter that supports the given element.
     *
     * @param element The PSI element to find an adapter for
     * @return The supporting adapter, or `null` if none matches
     */
    private fun findAdapter(element: PsiElement): PsiLanguageAdapter? {
        return adapters.firstOrNull { it.supportsElement(element) }
    }

    // ========== EOL Comment Extraction Utilities ==========

    private fun Sequence<PsiElement>.findEolComment(
        stopInUnexpectedElement: (PsiElement) -> Boolean = { true }
    ): String? {
        for (next in this) {
            when {
                next.isWhiteSpace() -> {
                    if (next.text?.contains('\n') == true) {
                        return null
                    }
                    continue
                }

                next is PsiComment -> {
                    return next.eolComment()
                }
            }
            if (stopInUnexpectedElement(next)) {
                return null
            }
        }

        return null
    }

    private fun PsiElement.isWhiteSpace() = this is PsiWhiteSpace

    private fun PsiComment.eolComment(): String? {
        if (this.tokenType == JavaTokenType.END_OF_LINE_COMMENT) {
            return text.trim().removePrefix(COMMENT_PREFIX).trimStart()
        }
        return null
    }

    private fun PsiElement.nextSiblings() = generateSequence { it.nextSibling }

    private fun PsiElement.generateSequence(nextFunction: (PsiElement) -> PsiElement?): Sequence<PsiElement> {
        return generateSequence(nextFunction(this), nextFunction)
    }

    private fun PsiElement.tinyText(): String {
        val text = this.text
        if (!text.isNullOrBlank()) {
            return text.trimEnd()
        }
        if (text.contains("\n")) {
            return "\n"
        }
        return text
    }
}
