package com.itangcent.easyapi.psi.adapter

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.javadoc.PsiDocTag
import com.itangcent.easyapi.psi.doc.DocComment
import com.itangcent.easyapi.psi.doc.DocTag

/**
 * PSI adapter for Java source files.
 *
 * Handles PSI element resolution for Java classes, methods, fields,
 * annotations, and documentation comments.
 *
 * This adapter also serves as the base implementation for other
 * JVM language adapters (Kotlin, Groovy, Scala) which delegate
 * to this class for most operations.
 *
 * ## Supported Elements
 * - Java classes (PsiClass)
 * - Java files (PsiJavaFile)
 * - Java methods (PsiMethod)
 * - Java fields (PsiField)
 * - Java annotations (PsiAnnotation)
 * - Javadoc comments
 *
 * @see PsiLanguageAdapter for the interface
 */
class JavaPsiAdapter : PsiLanguageAdapter {

    companion object {
        private val DOC_COMMENT_PREFIXES = listOf("*", "///", "//")
    }

    override fun supportsElement(element: PsiElement): Boolean {
        return element.containingFile?.language?.id?.equals("JAVA", true) == true
    }

    override fun resolveClass(element: PsiElement): PsiClass? {
        return when (element) {
            is PsiClass -> element
            is PsiJavaFile -> element.classes.firstOrNull()
            else -> element.parent as? PsiClass
        }
    }

    override fun resolveMethods(psiClass: PsiClass): List<PsiMethod> = psiClass.methods.toList()

    override fun resolveFields(psiClass: PsiClass): List<PsiField> = psiClass.allFields.toList()

    override fun resolveAnnotations(element: PsiElement): List<PsiAnnotation> {
        val owner = element as? com.intellij.psi.PsiModifierListOwner ?: return emptyList()
        return owner.annotations.toList()
    }

    override fun resolveDocComment(element: PsiElement): DocComment? {
        val owner = element as? PsiDocCommentOwner ?: return null
        val psiDocComment = owner.docComment ?: return null

        val text = psiDocComment.text
        val tags = psiDocComment.tags.mapNotNull { tag ->
            val tagName = tag.name.removePrefix("@")
            val tagValue = extractTagValue(tag)
            if (tagName.isNotEmpty()) {
                DocTag(tagName, tagValue)
            } else {
                null
            }
        }

        return DocComment(text = text, tags = tags)
    }

    override fun resolveEnumConstants(psiClass: PsiClass): List<String> {
        if (!psiClass.isEnum) return emptyList()
        return psiClass.fields.filterIsInstance<PsiEnumConstant>().map { it.name ?: "" }.filter { it.isNotEmpty() }
    }

    /**
     * Extracts the value from a PsiDocTag, handling multi-line values correctly.
     *
     * This method implements sophisticated tag value extraction:
     * - Removes the tag name prefix
     * - Handles multi-line tag values
     * - Removes comment prefixes (*, ///, //)
     * - Preserves proper formatting
     */
    private fun extractTagValue(tag: PsiDocTag): String {
        val lines = tag.text.lines()
        if (lines.isEmpty()) return ""

        // First line: remove tag name
        var result = lines[0].removePrefix(tag.nameElement.text).trimStart()

        // For single-line tags, return immediately
        if (lines.size == 1) {
            return result
        }

        // Multi-line tags: process remaining lines
        for (i in 1 until lines.size) {
            val processedLine = lines[i].trim()
                .removeCommentPrefix()
                .takeIf { it.isNotBlank() }

            if (processedLine != null) {
                result = "$result\n$processedLine"
            }
        }

        return result
    }

    /**
     * Removes common Javadoc comment prefixes from a line.
     */
    private fun String.removeCommentPrefix(): String {
        for (prefix in DOC_COMMENT_PREFIXES) {
            if (this.startsWith(prefix)) {
                return this.removePrefix(prefix).trim()
            }
        }
        return this
    }
}
