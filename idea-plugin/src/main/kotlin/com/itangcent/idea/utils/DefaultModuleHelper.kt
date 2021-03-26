package com.itangcent.idea.utils

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.idea.psi.PsiResource
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.ActionUtils
import org.apache.commons.lang3.StringUtils
import java.io.File

@Singleton
class DefaultModuleHelper : ModuleHelper {

    @Inject
    private val logger: Logger? = null

    @Inject
    private val project: Project? = null

    @Inject
    private val ruleComputer: RuleComputer? = null

    @Inject
    private val actionContext: ActionContext? = null

    //region find module
    override fun findModule(resource: Any): String? {
        return actionContext!!.callInReadUI {
            when (resource) {
                is PsiResource -> findModule(resource.resource() ?: resource.resourceClass()
                ?: return@callInReadUI null)
                is PsiMethod -> findModule(resource)
                is PsiClass -> findModule(resource)
                is PsiFile -> findModule(resource)
                else -> null
            }
        }
    }

    override fun findModule(psiMethod: PsiMethod): String? {

        val moduleByRule = ruleComputer!!.computer(ClassExportRuleKeys.MODULE, psiMethod)
        if (moduleByRule.notNullOrBlank()) {
            return moduleByRule
        }


        val containingClass = psiMethod.containingClass
        if (containingClass != null) {
            return findModule(containingClass)
        }

        val module = ModuleUtil.findModuleForPsiElement(psiMethod)
        if (module != null) {
            return module.name
        }
        return null
    }

    override fun findModule(cls: PsiClass): String? {

        val moduleByRule = ruleComputer!!.computer(ClassExportRuleKeys.MODULE, cls)

        if (moduleByRule.notNullOrBlank()) {
            return moduleByRule
        }

        val module = ModuleUtil.findModuleForPsiElement(cls)
        if (module != null) {
            return module.name
        }

        return findModule(cls.containingFile)
    }

    override fun findModule(psiFile: PsiFile): String? {
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

    override fun findModuleByPath(path: String?): String? {
        if (path == null) return null
        var module: String? = null
        try {
            var currentPath = path
            if (File.separatorChar != '/' && currentPath.contains(File.separatorChar)) {
                currentPath = currentPath.replace(File.separatorChar, '/')
            }
            when {
                currentPath.contains(src) -> currentPath = StringUtils.substringBefore(currentPath, src)
                currentPath.contains(main) -> currentPath = StringUtils.substringBefore(currentPath, main)
                currentPath.contains(java) -> currentPath = StringUtils.substringBefore(currentPath, java)
                currentPath.contains(kotlin) -> currentPath = StringUtils.substringBefore(currentPath, kotlin)
                currentPath.contains(scala) -> currentPath = StringUtils.substringBefore(currentPath, scala)
            }
            module = StringUtils.substringAfterLast(currentPath, "/")
        } catch (e: Exception) {
            logger!!.traceError("error in findCurrentPath", e)

        }
        return module

    }
    //endregion

    companion object {
        private const val src = "/src/"
        private const val main = "/main/"
        private const val java = "/java/"
        private const val kotlin = "/kotlin/"
        private const val scala = "/scala/"

    }
}