package com.itangcent.idea.plugin.api

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.util.DocCommentUtils

class ResourceHelper {

    @Inject
    private val actionContext: ActionContext? = null

    fun findResourceClass(resource: Any): PsiClass? {
        return when (resource) {
            is PsiMethod -> actionContext!!.callInReadUI { resource.containingClass }
            is PsiClass -> resource
            else -> null
        }
    }


    fun findAttrOfClass(cls: PsiClass): String? {
        val docComment = actionContext!!.callInReadUI { cls.docComment }
        val docText = DocCommentUtils.getAttrOfDocComment(docComment)
        return when {
            docText.isNullOrBlank() -> cls.name
            else -> docText
        }
    }
}