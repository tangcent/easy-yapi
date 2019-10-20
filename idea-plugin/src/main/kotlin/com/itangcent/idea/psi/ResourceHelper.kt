package com.itangcent.idea.psi

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.idea.plugin.api.export.DocParseHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.DocHelper
import org.apache.commons.lang3.StringUtils

@Singleton
class ResourceHelper {

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val docHelper: DocHelper? = null

    @Inject
    private val docParseHelper: DocParseHelper? = null

    fun findResourceClass(resource: Any): PsiClass? {
        return when (resource) {
            is PsiResource -> resource.resourceClass()
            is PsiMethod -> actionContext!!.callInReadUI { resource.containingClass }
            is PsiClass -> resource
            else -> null
        }
    }

    /**
     * todo:should be move to DocHelper
     */
    fun findAttrOfClass(cls: PsiClass): String? {
        val docText = docHelper!!.getAttrOfDocComment(cls)
        return when {
            StringUtils.isBlank(docText) -> cls.name
            docParseHelper != null -> docParseHelper.resolveLinkInAttr(docText, cls)
            else -> docText
        }
    }

}