package com.itangcent.idea.psi

import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiSuperMethodUtil
import org.jetbrains.annotations.NotNull

object PsiMethodUtil {
    fun isSuperMethod(@NotNull method: PsiMethod, @NotNull superMethod: PsiMethod): Boolean {
        if (method.name != superMethod.name) {
            return false
        }
        if (method.parameters.size != superMethod.parameters.size) {
            return false
        }
        return PsiSuperMethodUtil.isSuperMethod(method, superMethod)
    }
}