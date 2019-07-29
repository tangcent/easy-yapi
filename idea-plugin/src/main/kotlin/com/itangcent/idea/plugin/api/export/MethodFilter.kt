package com.itangcent.idea.plugin.api.export

import com.google.inject.ImplementedBy
import com.google.inject.Singleton
import com.intellij.psi.PsiMethod

@ImplementedBy(EmptyMethodFilter::class)
interface MethodFilter {
    fun checkMethod(method: PsiMethod): Boolean
}

@Singleton
class EmptyMethodFilter : MethodFilter {
    override fun checkMethod(method: PsiMethod): Boolean {
        return true
    }

}