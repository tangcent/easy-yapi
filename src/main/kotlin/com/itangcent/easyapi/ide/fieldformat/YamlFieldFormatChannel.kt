package com.itangcent.easyapi.ide.fieldformat

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.psi.JsonOption
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.psi.model.toYaml

/**
 * Field-format channel for YAML output.
 *
 * Uses `JsonOption.ALL` (same as JSON5 — includes comment metadata in the
 * model, though [YamlFormatter][com.itangcent.easyapi.exporter.formatter.YamlFormatter]
 * does not render comments). Delegates the pure rendering to
 * [ObjectModel.toYaml][com.itangcent.easyapi.psi.model.toYaml].
 */
class YamlFieldFormatChannel : FieldFormatChannel {
    override val id: String = "yaml"
    override val displayName: String = "YAML"
    override val actionText: String = "ToYaml"

    override suspend fun format(project: Project, psiClass: PsiClass): String =
        PsiClassHelper.getInstance(project)
            .buildObjectModel(psiClass, option = JsonOption.ALL)
            ?.toYaml() ?: ""
}
