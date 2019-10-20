package com.itangcent.idea.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

interface PsiResource {

    fun resourceClass(): PsiClass?

    fun resource(): PsiElement?

}