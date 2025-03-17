package com.itangcent.idea.plugin.api.infer

import com.google.inject.ProvidedBy
import com.intellij.psi.PsiMethod

@ProvidedBy(MethodInferHelperProvider::class)
interface MethodInferHelper {

    fun inferReturn(psiMethod: PsiMethod, option: Int = DefaultMethodInferHelper.DEFAULT_OPTION): Any?

    fun inferReturn(
        psiMethod: PsiMethod,
        caller: Any? = null,
        args: Array<Any?>?,
        option: Int = DefaultMethodInferHelper.DEFAULT_OPTION
    ): Any?
} 