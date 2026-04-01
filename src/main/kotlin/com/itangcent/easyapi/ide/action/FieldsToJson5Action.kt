package com.itangcent.easyapi.ide.action

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.psi.JsonOption
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter

/**
 * Action to convert class fields to JSON5 format.
 *
 * Builds an object model from the class fields and formats it as JSON5,
 * which supports comments, trailing commas, and unquoted keys.
 *
 * @see FieldFormatAction for the base class
 * @see ObjectModelJsonConverter for JSON5 conversion
 */
class FieldsToJson5Action : FieldFormatAction("Fields To JSON5") {
    override suspend fun format(project: Project, actionContext: ActionContext, psiClass: PsiClass): String {
        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, actionContext, option = JsonOption.ALL, maxDepth = 10)
        return model?.let { ObjectModelJsonConverter.toJson5(it) } ?: ""
    }
}
