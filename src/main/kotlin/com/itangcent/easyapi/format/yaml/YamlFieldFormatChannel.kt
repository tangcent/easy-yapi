package com.itangcent.easyapi.format.yaml

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.format.spi.FieldFormatChannel
import com.itangcent.easyapi.core.ide.PropertiesService

/**
 * Field-format channel for YAML output.
 *
 * Delegates to [PropertiesService] because YAML, like Properties, honors the
 * `properties.prefix` rule (resolved from `@ConfigurationProperties(prefix=...)`
 * via `RuleEngine`). The prefix is rendered as nested keys — mirroring Spring
 * Boot's `application.yml` semantics. The pure rendering lives in
 * [ObjectModel.toYaml][com.itangcent.easyapi.format.spi.toYaml], called by
 * `PropertiesService.toYaml` after prefix resolution.
 */
class YamlFieldFormatChannel : FieldFormatChannel {
    override val id: String = "yaml"
    override val displayName: String = "YAML"
    override val actionText: String = "ToYaml"

    override suspend fun format(project: Project, psiClass: PsiClass): String =
        PropertiesService.getInstance(project).toYaml(psiClass)
}
