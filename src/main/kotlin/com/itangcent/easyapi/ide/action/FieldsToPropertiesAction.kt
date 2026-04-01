package com.itangcent.easyapi.ide.action

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.format.PropertiesFormatter
import com.itangcent.easyapi.psi.PsiClassHelper

/**
 * Action to convert class fields to Java Properties format.
 *
 * Builds an object model from the class fields and formats it as
 * key-value pairs suitable for .properties files.
 *
 * @see FieldFormatAction for the base class
 */
class FieldsToPropertiesAction : FieldFormatAction("Fields To Properties") {
    override suspend fun format(project: Project, actionContext: ActionContext, psiClass: PsiClass): String {
        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, actionContext, maxDepth = 10)
            ?: return ""
        return PropertiesFormatter().format(model)
    }
}
