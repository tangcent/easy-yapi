package com.itangcent.easyapi.ide.action

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.format.PropertiesFormatter
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine

/**
 * Action to convert class fields to Java Properties format.
 *
 * Builds an object model from the class fields and formats it as
 * key-value pairs suitable for .properties files.
 *
 * Resolves the `properties.prefix` rule (typically from
 * `@ConfigurationProperties(prefix=...)`) and prepends it to all
 * generated property keys.
 *
 * @see FieldFormatAction for the base class
 */
class FieldsToPropertiesAction : FieldFormatAction("Fields To Properties") {
    override suspend fun format(project: Project, psiClass: PsiClass): String {
        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, maxDepth = 10)
            ?: return ""
        val ruleEngine = RuleEngine.getInstance(project)
        val prefix = ruleEngine.evaluate(RuleKeys.PROPERTIES_PREFIX, psiClass).orEmpty()
        return PropertiesFormatter().format(model, prefix)
    }
}
