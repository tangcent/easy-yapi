package com.itangcent.idea.plugin.api.export

import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.DocCommentUtils

@Singleton
open class DefaultLinkResolver : LinkResolver {
    override fun linkToClass(linkClass: Any): String? {
        if (linkClass !is PsiClass) {
            return "[$linkClass]"
        }
        val attrOfClass = DocCommentUtils.getAttrOfDocComment(linkClass.docComment)
        return when {
            attrOfClass.isNullOrBlank() -> "[${linkClass.name}]"
            else -> "[$attrOfClass]"
        }
    }

    override fun linkToMethod(linkMethod: Any): String? {
        if (linkMethod !is PsiMethod) {
            return "[$linkMethod]"
        }
        val attrOfMethod = DocCommentUtils.getAttrOfDocComment(linkMethod.docComment)
        return when {
            attrOfMethod.isNullOrBlank() -> "[${PsiClassUtils.fullNameOfMethod(linkMethod)}]"
            else -> "[$attrOfMethod]"
        }
    }

    override fun linkToProperty(linkField: Any): String? {
        if (linkField !is PsiField) {
            return "[$linkField]"
        }
        val attrOfProperty = DocCommentUtils.getAttrOfDocComment(linkField.docComment)
        return when {
            attrOfProperty.isNullOrBlank() -> "[${PsiClassUtils.fullNameOfField(linkField)}]"
            else -> "[$attrOfProperty]"
        }
    }

}