package com.itangcent.easyapi.psi.adapter

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.psi.KtDeclaration
import com.itangcent.easyapi.psi.doc.DocComment
import com.itangcent.easyapi.psi.doc.DocTag
import org.jetbrains.kotlin.asJava.elements.KtLightElement

/**
 * PSI adapter for Kotlin source files.
 *
 * Handles PSI element resolution for Kotlin classes. Delegates to
 * [JavaPsiAdapter] for operations where Kotlin's light PSI bridge
 * is compatible with Java PSI (class resolution, methods, fields,
 * annotations, enum constants).
 *
 * ## Kotlin-specific differences from Java PSI
 *
 * - **supportsElement**: Kotlin light PSI elements (`KtLightClass`, `KtLightMethod`)
 *   may report their containing file's language as "JAVA" through the light PSI bridge.
 *   This adapter checks both the file language ID and the element type (`KtLightElement`,
 *   `KtDeclaration`) to correctly identify Kotlin elements.
 *
 * - **resolveDocComment**: Kotlin uses KDoc (not Javadoc). `KtLightElement`
 *   does NOT implement `PsiDocCommentOwner`, so the Java adapter's doc
 *   resolution returns null for Kotlin elements. This adapter navigates
 *   from light PSI to native Kotlin PSI via [KtLightElement.kotlinOrigin]
 *   to access [KtDeclaration.docComment] (a [KDoc] instance), then parses
 *   KDoc tags ([KDocSection]/[KDocTag]) into [DocTag] entries.
 *
 * - **resolveClass**: Kotlin light classes (`KtLightClass`) implement `PsiClass`,
 *   so the Java adapter handles this correctly. No override needed.
 *
 * ## KDoc Resolution Strategy
 * 1. For [KtLightElement] instances, navigate to [KtDeclaration] via `kotlinOrigin`
 * 2. For direct [KtDeclaration] instances, access `docComment` directly
 * 3. Fall back to [JavaPsiAdapter.resolveDocComment] for Javadoc-style comments
 *
 * @see PsiLanguageAdapter for the interface
 * @see JavaPsiAdapter for the delegate implementation
 */
class KotlinPsiAdapter : PsiLanguageAdapter {

    private val delegate = JavaPsiAdapter()

    override fun supportsElement(element: PsiElement): Boolean {
        // Check language ID from the containing file
        val id = element.containingFile?.language?.id
        if (id != null && (id.equals("kotlin", true) || id.equals("Kotlin", true))) {
            return true
        }
        // KtLightElement wraps Kotlin source but may report language as "JAVA"
        // through the light PSI bridge. Check the element type directly.
        if (element is KtLightElement<*, *> || element is KtDeclaration) {
            return true
        }
        return false
    }

    override fun resolveClass(element: PsiElement): PsiClass? = delegate.resolveClass(element)

    override fun resolveMethods(psiClass: PsiClass): List<PsiMethod> = delegate.resolveMethods(psiClass)

    override fun resolveFields(psiClass: PsiClass): List<PsiField> = delegate.resolveFields(psiClass)

    override fun resolveAnnotations(element: PsiElement): List<PsiAnnotation> = delegate.resolveAnnotations(element)

    override fun resolveDocComment(element: PsiElement): DocComment? {
        val kDoc = findKDoc(element) ?: return delegate.resolveDocComment(element)
        return parseKDoc(kDoc)
    }

    override fun resolveEnumConstants(psiClass: PsiClass): List<String> = delegate.resolveEnumConstants(psiClass)

    private fun findKDoc(element: PsiElement): KDoc? {
        return when (element) {
            is KtLightElement<*, *> -> {
                val kotlinOrigin = element.kotlinOrigin ?: return null
                if (kotlinOrigin is KtDeclaration) kotlinOrigin.docComment else null
            }
            is KtDeclaration -> element.docComment
            else -> null
        }
    }

    private fun parseKDoc(kDoc: KDoc): DocComment {
        val text = kDoc.text
        val tags = mutableListOf<DocTag>()

        for (section in kDoc.children.filterIsInstance<KDocSection>()) {
            for (tag in section.children.filterIsInstance<KDocTag>()) {
                val name = tag.name ?: continue
                val subjectName = tag.getSubjectName()
                val content = tag.contentOrLink() ?: ""
                // Include subject name in value to match Javadoc convention:
                // @param id the user ID → DocTag("param", "id the user ID")
                val value = if (subjectName != null && content.isNotEmpty()) {
                    "$subjectName $content"
                } else {
                    subjectName ?: content
                }
                tags.add(DocTag(name, value))
            }
        }

        return DocComment(text = text, tags = tags)
    }

    private fun KDocTag.contentOrLink(): String? {
        val content = getContent()
        if (content.isNotBlank()) return content
        return getSubjectLink()?.text
    }
}
