package com.itangcent.easyapi.ide

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.psi.model.toProperties
import com.itangcent.easyapi.psi.model.toYaml
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine

/**
 * Project-scoped service that converts a [PsiClass] into config-format text
 * (Java Properties or YAML).
 *
 * Owns the only stateful part of field-format conversion: resolving the
 * `properties.prefix` rule (typically from `@ConfigurationProperties(prefix=...)`)
 * via [RuleEngine]. The pure rendering is delegated to
 * [ObjectModel.toProperties][com.itangcent.easyapi.psi.model.toProperties] and
 * [ObjectModel.toYaml][com.itangcent.easyapi.psi.model.toYaml].
 *
 * JSON and JSON5 conversions are pure functions of the model and live as
 * extension functions (`ObjectModel.toJson`, `ObjectModel.toJson5`) — they
 * need no project state. Properties and YAML both honor the config prefix
 * (flat dot-path for `.properties`, nested keys for `.yml`) and therefore
 * route through this service.
 *
 * ## Parity contract
 *
 * The `JsonOption` is left at its default (`ALL`) to match the original
 * `FieldsToPropertiesAction` / `FieldsToYamlAction`. Returns `""` if the
 * class cannot be modeled.
 *
 * @see com.itangcent.easyapi.psi.model.toProperties for the pure Properties rendering
 * @see com.itangcent.easyapi.psi.model.toYaml for the pure YAML rendering
 */
@Service(Service.Level.PROJECT)
class PropertiesService(private val project: Project) {

    private val helper: PsiClassHelper = PsiClassHelper.getInstance(project)
    private val ruleEngine: RuleEngine = RuleEngine.getInstance(project)

    /**
     * Fields → Java Properties.
     *
     * Resolves the `properties.prefix` rule and prepends it to all generated
     * property keys (dot-separated, e.g. `app.config.id=0`). Returns `""` if
     * the class cannot be modeled.
     */
    suspend fun toProperties(psiClass: PsiClass): String {
        val model = helper.buildObjectModel(psiClass) ?: return ""
        val prefix = ruleEngine.evaluate(RuleKeys.PROPERTIES_PREFIX, psiClass).orEmpty()
        return model.toProperties(prefix)
    }

    /**
     * Fields → YAML.
     *
     * Resolves the `properties.prefix` rule and renders it as nested keys
     * wrapping the model (mirrors `@ConfigurationProperties` for
     * `application.yml`). Returns `""` if the class cannot be modeled.
     */
    suspend fun toYaml(psiClass: PsiClass): String {
        val model = helper.buildObjectModel(psiClass) ?: return ""
        val prefix = ruleEngine.evaluate(RuleKeys.PROPERTIES_PREFIX, psiClass).orEmpty()
        return model.toYaml(prefix)
    }

    companion object {
        fun getInstance(project: Project): PropertiesService =
            project.getService(PropertiesService::class.java)
    }
}
