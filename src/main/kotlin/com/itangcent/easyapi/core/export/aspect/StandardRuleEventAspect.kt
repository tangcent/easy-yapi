package com.itangcent.easyapi.core.export.aspect

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.engine.RuleEngine

/**
 * Fires the seven standard lifecycle rule events.
 *
 * This is the only production source that references
 * `RuleKeys.API_*_PARSE_*` and `RuleKeys.EXPORT_AFTER`.
 *
 * `export.after` binds the endpoint to the script context as `api`, preserving
 * the mutation surface existing rules rely on (e.g. `api.appendDesc(...)`).
 */
class StandardRuleEventAspect(private val project: Project) : ApiParseAspect {

    private val engine = RuleEngine.getInstance(project)

    override suspend fun beforeClass(ctx: ClassParseContext) =
        engine.evaluate(RuleKeys.API_CLASS_PARSE_BEFORE, ctx.psiClass)

    override suspend fun afterClass(ctx: ClassParseContext) =
        engine.evaluate(RuleKeys.API_CLASS_PARSE_AFTER, ctx.psiClass)

    override suspend fun beforeMethod(ctx: MethodParseContext) =
        engine.evaluate(RuleKeys.API_METHOD_PARSE_BEFORE, ctx.resolvedMethod)

    override suspend fun afterMethod(ctx: MethodParseContext) =
        engine.evaluate(RuleKeys.API_METHOD_PARSE_AFTER, ctx.resolvedMethod)

    override suspend fun beforeParam(ctx: ParamParseContext) =
        engine.evaluate(RuleKeys.API_PARAM_PARSE_BEFORE, ctx.parameter)

    override suspend fun afterParam(ctx: ParamParseContext) =
        engine.evaluate(RuleKeys.API_PARAM_PARSE_AFTER, ctx.parameter)

    override suspend fun afterEndpoint(ctx: EndpointParseContext) =
        engine.evaluate(RuleKeys.EXPORT_AFTER, ctx.resolvedMethod) { it.setExt("api", ctx.endpoint) }
}
