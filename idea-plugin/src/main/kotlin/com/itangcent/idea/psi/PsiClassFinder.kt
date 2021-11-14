package com.itangcent.idea.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.intellij.context.ActionContext

object PsiClassFinder {

    fun findClass(fqClassName: String, project: Project): PsiClass? {
        return JavaPsiFacade.getInstance(project).findClass(fqClassName, GlobalSearchScope.allScope(project))
    }

    fun findClass(fqClassName: String): PsiClass? {
        val project = ActionContext.getContext()?.instance(Project::class) ?: return null
        return findClass(fqClassName, project)
    }
}