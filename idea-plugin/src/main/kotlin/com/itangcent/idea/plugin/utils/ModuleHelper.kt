package com.itangcent.idea.plugin.utils

import com.google.inject.Inject
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.itangcent.idea.plugin.api.export.CommonRules
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.ActionUtils
import org.apache.commons.lang3.StringUtils

class ModuleHelper {

    @Inject
    private val logger: Logger? = null

    @Inject
    private val project: Project? = null

    @Inject
    private val commonRules: CommonRules? = null

    //region find module
    fun findModule(resource: Any): String? {
        return when (resource) {
            is PsiMethod -> findModule(resource)
            is PsiClass -> findModule(resource)
            is PsiFile -> findModule(resource)
            else -> null
        }
    }

    fun findModule(psiMethod: PsiMethod): String? {
        var module = ModuleUtil.findModuleForPsiElement(psiMethod)
        if (module != null) {
            return module.name
        }
        val containingClass = psiMethod.containingClass
        if (containingClass != null) {
            return findModule(containingClass)
        }
        return null
    }

    fun findModule(cls: PsiClass): String? {
        val module = ModuleUtil.findModuleForPsiElement(cls)
        if (module != null) {
            return module.name
        }
        val moduleRules = commonRules?.readModuleRules()
        if (!moduleRules.isNullOrEmpty()) {
            val moduleByRule = moduleRules
                    .map { it(cls, cls, cls) }
                    .firstOrNull { it != null }
            if (!moduleByRule.isNullOrBlank()) {
                return moduleByRule
            }
        }
        return findModule(cls.containingFile)
    }

    fun findModule(psiFile: PsiFile): String? {
        var module = ModuleUtil.findModuleForPsiElement(psiFile)
        if (module != null) {
            return module.name
        }
        module = ModuleUtil.findModuleForFile(psiFile.virtualFile, project!!)
        if (module != null) {
            return module.name
        }
        val currentPath = ActionUtils.findCurrentPath(psiFile)
        return findModuleByPath(currentPath)
    }

    fun findModuleByPath(path: String?): String? {
        if (path == null) return null
        var module: String? = null
        try {
            var currentPath = path
            when {
                currentPath.contains("/src/") -> currentPath = StringUtils.substringBefore(currentPath, "/src/")
                currentPath.contains("/main/") -> currentPath = StringUtils.substringBefore(currentPath, "/main/")
                currentPath.contains("/java/") -> currentPath = StringUtils.substringBefore(currentPath, "/java/")
                currentPath.contains("/kotlin/") -> currentPath = StringUtils.substringBefore(currentPath, "/kotlin/")
            }
            module = StringUtils.substringAfterLast(currentPath, "/")
        } catch (e: Exception) {
            logger!!.error("error in findCurrentPath:" + e.toString())
        }
        return module

    }
    //endregion
}