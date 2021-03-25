package com.itangcent.idea.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod

class PsiMethodResource : PsiResource {

    private val psiMethod: PsiMethod
    private val psiClass: PsiClass

    constructor(psiMethod: PsiMethod, psiClass: PsiClass) {
        this.psiMethod = psiMethod
        this.psiClass = psiClass
    }

    override fun resourceClass(): PsiClass? {
        return psiClass
    }

    override fun resource(): PsiElement? {
        return psiMethod
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PsiMethodResource

        if (psiMethod != other.psiMethod) return false
        if (psiClass != other.psiClass) return false

        return true
    }

    override fun hashCode(): Int {
        var result = psiMethod.hashCode()
        result = 31 * result + psiClass.hashCode()
        return result
    }

}