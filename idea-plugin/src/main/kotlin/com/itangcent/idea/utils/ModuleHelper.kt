package com.itangcent.idea.utils

import com.google.inject.ImplementedBy
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod

/**
 * find [Module] name
 */
@ImplementedBy(DefaultModuleHelper::class)
interface ModuleHelper {

    fun findModule(resource: Any): String?

    fun findModule(psiMethod: PsiMethod): String?

    fun findModule(cls: PsiClass): String?

    fun findModule(psiFile: PsiFile): String?

    fun findModuleByPath(path: String?): String?
}