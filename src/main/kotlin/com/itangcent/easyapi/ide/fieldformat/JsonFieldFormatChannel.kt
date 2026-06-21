package com.itangcent.easyapi.ide.fieldformat

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.psi.JsonOption
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.psi.model.toJson

/**
 * Field-format channel for standard JSON output.
 *
 * Uses `JsonOption.READ_GETTER_OR_SETTER` (parity with the original
 * `FieldsToJsonAction` — only getters/setters, no comments). Delegates the
 * pure rendering to [ObjectModel.toJson][com.itangcent.easyapi.psi.model.toJson],
 * which in turn delegates to
 * [ObjectModelJsonConverter.toJson][com.itangcent.easyapi.psi.model.ObjectModelJsonConverter.toJson].
 */
class JsonFieldFormatChannel : FieldFormatChannel {
    override val id: String = "json"
    override val displayName: String = "JSON"
    override val actionText: String = "ToJson"

    override suspend fun format(project: Project, psiClass: PsiClass): String =
        PsiClassHelper.getInstance(project)
            .buildObjectModel(psiClass, JsonOption.READ_GETTER_OR_SETTER)
            ?.toJson() ?: ""
}
