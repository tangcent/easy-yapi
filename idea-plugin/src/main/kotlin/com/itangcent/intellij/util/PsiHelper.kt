package com.itangcent.intellij.util

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil

//Everything contained here will be moved to `intellij-kotlin` in the future
@Deprecated("use PsiClassUtils", ReplaceWith("com.itangcent.intellij.psi.PsiClassUtils"))
object PsiHelper {

    @Deprecated("use PsiClassUtils", ReplaceWith("com.itangcent.intellij.psi.PsiClassUtils.isInterface"))
    fun isInterface(psiType: PsiType): Boolean {
        return PsiTypesUtil.getPsiClass(psiType)?.isInterface ?: false
    }

    @Deprecated("use PsiClassUtils", ReplaceWith("com.itangcent.intellij.psi.PsiClassUtils.hasImplement"))
    fun hasImplement(psiClass: PsiClass?, interCls: PsiClass?): Boolean {
        if (psiClass == null || interCls == null) {
            return false
        }
        if (psiClass == interCls || psiClass.isInheritor(interCls, true)) {
            return true
        }
        val qualifiedName = interCls.qualifiedName
        if (psiClass.qualifiedName == qualifiedName) {
            return true
        }
        if (psiClass.isInterface) {
            var from = psiClass.superClass
            while (from != null) {
                if (from == psiClass || from.qualifiedName == qualifiedName) {
                    return true
                }

                from = from.superClass
            }
            return false
        } else {
            return psiClass.interfaces.any { it == interCls || it.qualifiedName == qualifiedName }
        }
    }
}