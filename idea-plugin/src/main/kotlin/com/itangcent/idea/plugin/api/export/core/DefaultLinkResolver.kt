package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PropertyUtil
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.intellij.jvm.DocHelper
import com.itangcent.intellij.jvm.psi.PsiClassUtil

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
            else -> "[${linkClass.name}($attrOfClass)]"
        }
    }

    override fun linkToMethod(linkMethod: Any): String? {
        if (linkMethod !is PsiMethod) {
            return "[$linkMethod]"
        }
        val fullNameOfMethod = PsiClassUtil.fullNameOfMethod(linkMethod)
        val attrOfMethod = docHelper!!.getAttrOfDocComment(linkMethod)
            ?.lines()?.first { it.isNotBlank() }
        if (attrOfMethod.notNullOrBlank()) {
            return "[$fullNameOfMethod($attrOfMethod)]"
        }

        //resolve getter
        if (PropertyUtil.isSimpleGetter(linkMethod)) {
            val field = PropertyUtil.getFieldOfGetter(linkMethod)
            if (field != null) {
                return linkToProperty(field)
            }
        }

        return "[$fullNameOfMethod]"
    }

    override fun linkToProperty(linkField: Any): String? {
        if (linkField !is PsiField) {
            return "[$linkField]"
        }
        val attrOfProperty = docHelper!!.getAttrOfField(linkField)
        val fullNameOfField = PsiClassUtil.fullNameOfField(linkField)
        return when {
            attrOfProperty.isNullOrBlank() -> {
                "[$fullNameOfField]"
            }

            else -> "[$fullNameOfField($attrOfProperty)]"
        }
    }

}