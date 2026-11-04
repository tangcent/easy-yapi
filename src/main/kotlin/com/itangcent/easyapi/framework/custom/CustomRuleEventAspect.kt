package com.itangcent.easyapi.framework.custom

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.export.aspect.ApiParseAspect
import com.itangcent.easyapi.core.export.aspect.ClassParseContext
import com.itangcent.easyapi.core.export.aspect.EndpointParseContext
import com.itangcent.easyapi.core.export.aspect.MethodParseContext
import com.itangcent.easyapi.core.rule.engine.RuleEngine

/**
 * Fires the five Custom-framework lifecycle rule events
 * (`custom.class.parse.before/after`, `custom.method.parse.before/after`,
 * `custom.export.after`).
 *
 * Registered through the `apiParseAspect` extension point and restricted to the
 * Custom framework via [appliesTo]. [order] is `1`, so the shared
 * [com.itangcent.easyapi.core.export.aspect.StandardRuleEventAspect] hooks run
 * first — preserving the historical "shared hook → custom hook" order.
 *
 * Parameter-level hooks are intentionally not overridden: [CustomRuleKeys] has
 * exactly five `CUSTOM_*` event keys and no parameter-level event. Its other
 * thirteen keys are value-read rules evaluated directly by
 * [CustomClassExporter].
 */
class CustomRuleEventAspect(private val project: Project) : ApiParseAspect {

    override val order: Int = 1

    /**
     * Compares against the same symbol the exporter's `frameworkName` uses, so
     * both sides cannot drift to different literal sources.
     */
    override fun appliesTo(frameworkName: String): Boolean =
        frameworkName == CustomApiRecognizer.FRAMEWORK_NAME

    private val engine = RuleEngine.getInstance(project)

    override suspend fun beforeClass(ctx: ClassParseContext) =
        engine.evaluate(CustomRuleKeys.CUSTOM_CLASS_PARSE_BEFORE, ctx.psiClass)

    override suspend fun afterClass(ctx: ClassParseContext) =
        engine.evaluate(CustomRuleKeys.CUSTOM_CLASS_PARSE_AFTER, ctx.psiClass)

    override suspend fun beforeMethod(ctx: MethodParseContext) =
        engine.evaluate(CustomRuleKeys.CUSTOM_METHOD_PARSE_BEFORE, ctx.resolvedMethod)

    override suspend fun afterMethod(ctx: MethodParseContext) =
        engine.evaluate(CustomRuleKeys.CUSTOM_METHOD_PARSE_AFTER, ctx.resolvedMethod)

    /**
     * Binds `api` because `custom.export.after` scripts depend on it, matching
     * the previous exporter-side behaviour.
     */
    override suspend fun afterEndpoint(ctx: EndpointParseContext) =
        engine.evaluate(CustomRuleKeys.CUSTOM_EXPORT_AFTER, ctx.resolvedMethod) {
            it.setExt("api", ctx.endpoint)
        }
}
