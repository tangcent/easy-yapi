package com.itangcent.idea.psi

import com.intellij.psi.PsiMethod
import com.itangcent.intellij.context.ActionContext

class PsiMethodSet {

    private val parsedMethods = HashSet<PsiMethod>()
    private val parsedMethodNames = HashSet<String>()

    fun add(method: PsiMethod): Boolean {
        val methodName = ActionContext.getContext()!!.callInReadUI { method.name }!!
        if (parsedMethodNames.add(methodName)) {
            parsedMethods.add(method)
            return true
        }
        if (parsedMethods.contains(method)) {
            return false
        }
        for (psiMethod in parsedMethods) {
            if (PsiMethodUtil.isSuperMethod(psiMethod, method)
                || PsiMethodUtil.isSuperMethod(psiMethod, method)
            ) {
                return false
            }
        }
        parsedMethods.add(method)
        return true
    }
}