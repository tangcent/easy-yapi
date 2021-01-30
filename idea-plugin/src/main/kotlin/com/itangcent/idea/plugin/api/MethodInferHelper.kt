package com.itangcent.idea.plugin.api

import com.google.inject.ImplementedBy
import com.intellij.psi.PsiMethod

@ImplementedBy(DefaultMethodInferHelper::class)
interface MethodInferHelper {

    fun inferReturn(psiMethod: PsiMethod, option: Int = DefaultMethodInferHelper.DEFAULT_OPTION): Any?

    fun inferReturn(psiMethod: PsiMethod, caller: Any? = null, args: Array<Any?>?, option: Int = DefaultMethodInferHelper.DEFAULT_OPTION): Any?

}