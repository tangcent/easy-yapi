package com.itangcent.easyapi.format.properties

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.format.spi.FieldFormatChannel
import com.itangcent.easyapi.core.ide.PropertiesService

/**
 * Field-format channel for Java Properties output.
 *
 * Delegates to [PropertiesService] because Properties, like YAML, needs
 * project-scoped state (resolving `properties.prefix` via `RuleEngine`).
 * The pure rendering lives in
 * [ObjectModel.toProperties][com.itangcent.easyapi.format.spi.toProperties],
 * called by `PropertiesService` after prefix resolution.
 */
class PropertiesFieldFormatChannel : FieldFormatChannel {
    override val id: String = "properties"
    override val displayName: String = "Properties"
    override val actionText: String = "ToProperties"

    override suspend fun format(project: Project, psiClass: PsiClass): String =
        PropertiesService.getInstance(project).toProperties(psiClass)
}
