package com.itangcent.idea.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

class PsiClassResource : PsiResource {

    private val psiClass: PsiClass

    constructor(psiClass: PsiClass) {
        this.psiClass = psiClass
    }

    override fun resourceClass(): PsiClass {
        return psiClass
    }

    override fun resource(): PsiElement {
        return psiClass
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PsiClassResource

        if (psiClass != other.psiClass) return false

        return true
    }

    override fun hashCode(): Int {
        return psiClass.hashCode()
    }

}