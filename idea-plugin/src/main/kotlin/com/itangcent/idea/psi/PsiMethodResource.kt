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

}