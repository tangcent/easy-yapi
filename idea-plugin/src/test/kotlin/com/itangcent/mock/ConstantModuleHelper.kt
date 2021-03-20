package com.itangcent.mock

import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.itangcent.idea.utils.ModuleHelper

@Singleton
class ConstantModuleHelper(private val module: String) : ModuleHelper {

    override fun findModule(resource: Any): String? {
        return module
    }

    override fun findModule(psiMethod: PsiMethod): String? {
        return module
    }

    override fun findModule(cls: PsiClass): String? {
        return module
    }

    override fun findModule(psiFile: PsiFile): String? {
        return module
    }

    override fun findModuleByPath(path: String?): String? {
        return module
    }

    companion object {
        val INSTANCE = ConstantModuleHelper("test_default")
    }
}