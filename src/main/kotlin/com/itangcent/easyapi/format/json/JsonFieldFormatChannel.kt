package com.itangcent.easyapi.format.json

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.format.spi.FieldFormatChannel
import com.itangcent.easyapi.format.spi.toJson
import com.itangcent.easyapi.core.psi.JsonOption
import com.itangcent.easyapi.core.psi.PsiClassHelper

/**
 * Field-format channel for standard JSON output.
 *
 * Uses `JsonOption.READ_GETTER_OR_SETTER` (parity with the original
 * `FieldsToJsonAction` — only getters/setters, no comments). Delegates the
 * pure rendering to [ObjectModel.toJson][com.itangcent.easyapi.format.spi.toJson],
 * which in turn delegates to
 * [ObjectModelJsonConverter.toJson][com.itangcent.easyapi.format.json.ObjectModelJsonConverter.toJson].
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
