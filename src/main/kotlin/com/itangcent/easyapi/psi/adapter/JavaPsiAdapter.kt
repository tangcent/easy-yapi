package com.itangcent.easyapi.psi.adapter

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
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
        val doc = owner.docComment?.text ?: return null
        return DocComment(text = doc, tags = parseTags(doc))
    }

    override fun resolveEnumConstants(psiClass: PsiClass): List<String> {
        if (!psiClass.isEnum) return emptyList()
        return psiClass.fields.filterIsInstance<PsiEnumConstant>().map { it.name ?: "" }.filter { it.isNotEmpty() }
    }

    private fun parseTags(doc: String): List<DocTag> {
        val tags = ArrayList<DocTag>()
        for (line in doc.lines()) {
            val trimmed = line.trim().trimStart('*').trim()
            if (!trimmed.startsWith("@")) continue
            val name = trimmed.substringAfter("@").substringBefore(" ").trim()
            val value = trimmed.substringAfter(" ", missingDelimiterValue = "").trim()
            if (name.isNotEmpty()) tags.add(DocTag(name, value))
        }
        return tags
    }
}
