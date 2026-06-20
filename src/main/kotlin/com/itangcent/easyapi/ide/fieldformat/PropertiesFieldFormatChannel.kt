package com.itangcent.easyapi.ide.fieldformat

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.ide.PropertiesService

/**
 * Field-format channel for Java Properties output.
 *
 * Delegates to [PropertiesService] because Properties is the only format that
 * needs project-scoped state (resolving `properties.prefix` via `RuleEngine`).
 * The pure rendering lives in
 * [ObjectModel.toProperties][com.itangcent.easyapi.psi.model.toProperties],
 * called by `PropertiesService` after prefix resolution.
 */
class PropertiesFieldFormatChannel : FieldFormatChannel {
    override val id: String = "properties"
    override val displayName: String = "Properties"
    override val actionText: String = "ToProperties"

    override suspend fun format(project: Project, psiClass: PsiClass): String =
        PropertiesService.getInstance(project).toProperties(psiClass)
}
