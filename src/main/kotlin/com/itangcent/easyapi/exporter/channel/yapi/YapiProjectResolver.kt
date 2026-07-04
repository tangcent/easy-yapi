package com.itangcent.easyapi.exporter.channel.yapi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.exporter.channel.yapi.YapiRuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine

/**
 * Resolves the YApi project for a method/class via the `yapi.project` rule key.
 * Extracted from [com.itangcent.easyapi.psi.helper.DocMetadataResolver] so the
 * shared resolver file can be identical between easy-api and easy-yapi.
 */
object YapiProjectResolver {

    suspend fun resolveYapiProject(
        engine: RuleEngine,
        method: PsiMethod
    ): String? {
        return engine.evaluate(YapiRuleKeys.YAPI_PROJECT, method)
    }

    suspend fun resolveYapiProject(
        engine: RuleEngine,
        psiClass: PsiClass
    ): String? {
        return engine.evaluate(YapiRuleKeys.YAPI_PROJECT, psiClass)
    }
}
