package com.itangcent.easyapi.mock

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiField
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType

object MockKit {
    
    fun mockPsiClass(name: String): PsiClass? = null
    
    fun mockPsiMethod(name: String, returnType: PsiType): PsiMethod? = null
    
    fun mockPsiField(name: String, type: PsiType): PsiField? = null
    
    fun mockPsiParameter(name: String, type: PsiType): PsiParameter? = null
    
    fun mockPsiType(canonicalText: String): PsiType? = null
}
