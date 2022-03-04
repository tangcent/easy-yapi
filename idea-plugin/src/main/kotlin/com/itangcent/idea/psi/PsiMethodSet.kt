package com.itangcent.idea.psi

import com.intellij.psi.PsiMethod

class PsiMethodSet {

    private val parsedMethods = HashSet<PsiMethod>()
    private val parsedMethodNames = HashSet<String>()

    fun add(method: PsiMethod): Boolean {
        if (parsedMethodNames.add(method.name)) {
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