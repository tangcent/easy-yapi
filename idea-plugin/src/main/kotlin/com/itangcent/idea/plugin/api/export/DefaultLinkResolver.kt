package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.itangcent.intellij.jvm.DocHelper
import com.itangcent.intellij.psi.PsiClassUtils

@Singleton
open class DefaultLinkResolver : LinkResolver {

    @Inject
    private val docHelper: DocHelper? = null

    override fun linkToClass(linkClass: Any): String? {
        if (linkClass !is PsiClass) {
            return "[$linkClass]"
        }
        val attrOfClass = docHelper!!.getAttrOfDocComment(linkClass)
        return when {
            attrOfClass.isNullOrBlank() -> "[${linkClass.name}]"
            else -> "[$attrOfClass]"
        }
    }

    override fun linkToMethod(linkMethod: Any): String? {
        if (linkMethod !is PsiMethod) {
            return "[$linkMethod]"
        }
        val attrOfMethod = docHelper!!.getAttrOfDocComment(linkMethod)
        return when {
            attrOfMethod.isNullOrBlank() -> "[${PsiClassUtils.fullNameOfMethod(linkMethod)}]"
            else -> "[$attrOfMethod]"
        }
    }

    override fun linkToProperty(linkField: Any): String? {
        if (linkField !is PsiField) {
            return "[$linkField]"
        }
        val attrOfProperty = docHelper!!.getAttrOfDocComment(linkField)
        return when {
            attrOfProperty.isNullOrBlank() -> "[${PsiClassUtils.fullNameOfField(linkField)}]"
            else -> "[$attrOfProperty]"
        }
    }

}