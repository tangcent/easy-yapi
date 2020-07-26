package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PropertyUtil
import com.itangcent.common.utils.notNullOrBlank
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
                ?.lines()?.first { !it.isBlank() }
        if (attrOfMethod.notNullOrBlank()) {
            return "[$attrOfMethod]"
        }

        //resolve getter
        if (PropertyUtil.isSimpleGetter(linkMethod)) {
            val field = PropertyUtil.getFieldOfGetter(linkMethod)
            if (field != null) {
                return linkToProperty(field)
            }
        }

        return "[${PsiClassUtils.fullNameOfMethod(linkMethod)}]"
    }

    override fun linkToProperty(linkField: Any): String? {
        if (linkField !is PsiField) {
            return "[$linkField]"
        }
        val attrOfProperty = docHelper!!.getAttrOfField(linkField)
        return when {
            attrOfProperty.isNullOrBlank() -> "[${PsiClassUtils.fullNameOfField(linkField)}]"
            else -> "[$attrOfProperty]"
        }
    }

}