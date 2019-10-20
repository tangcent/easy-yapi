package com.itangcent.idea.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

class PsiClassResource : PsiResource {

    private val psiClass: PsiClass

    constructor(psiClass: PsiClass) {
        this.psiClass = psiClass
    }

    override fun resourceClass(): PsiClass? {
        return psiClass
    }

    override fun resource(): PsiElement? {
        return psiClass
    }

}