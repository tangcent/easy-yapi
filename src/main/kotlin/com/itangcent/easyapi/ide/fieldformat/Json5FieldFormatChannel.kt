package com.itangcent.easyapi.ide.fieldformat

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.psi.JsonOption
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.psi.model.toJson5

/**
 * Field-format channel for JSON5 output (with comments).
 *
 * Uses `JsonOption.ALL` (parity with the original `FieldsToJson5Action` —
 * includes comments). Delegates the pure rendering to
 * [ObjectModel.toJson5][com.itangcent.easyapi.psi.model.toJson5], which in
 * turn delegates to
 * [ObjectModelJsonConverter.toJson5][com.itangcent.easyapi.psi.model.ObjectModelJsonConverter.toJson5].
 */
class Json5FieldFormatChannel : FieldFormatChannel {
    override val id: String = "json5"
    override val displayName: String = "JSON5"
    override val actionText: String = "ToJson5"

    override suspend fun format(project: Project, psiClass: PsiClass): String =
        PsiClassHelper.getInstance(project)
            .buildObjectModel(psiClass, option = JsonOption.ALL)
            ?.toJson5() ?: ""
}
