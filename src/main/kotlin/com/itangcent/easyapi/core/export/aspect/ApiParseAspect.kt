package com.itangcent.easyapi.core.export.aspect

/**
 * Contract for a cross-cutting participant in the API parse lifecycle.
 *
 * Implementations are discovered through the `apiParseAspect` extension point
 * (project-scoped, dynamic) and are invoked by [ApiParseAspects]. Each aspect
 * owns one coherent group of lifecycle hooks; ordering between aspects is
 * controlled by [order].
 *
 * ## Contract
 * - [order] only controls ordering, never whether an aspect runs.
 * - [isEnabled] and [appliesTo] decide whether an aspect participates; they are
 *   re-evaluated on every dispatch call (never cached).
 * - Hook bodies run on the caller's thread. They must **not** switch threads:
 *   script evaluation already happens on the background pool inside
 *   `Jsr223ScriptParser`, and any PSI access must self-protect.
 */
interface ApiParseAspect {

    /**
     * Ascending execution order. Standard events are `0`, framework-scoped
     * events are `1`, post-processing aspects should use `>= 100`.
     */
    val order: Int get() = 0

    /** Settings-driven enablement; evaluated on every dispatch. */
    fun isEnabled(): Boolean = true

    /** Framework condition; receives `ClassExporter.frameworkName`. */
    fun appliesTo(frameworkName: String): Boolean = true

    suspend fun beforeClass(ctx: ClassParseContext) {}

    suspend fun afterClass(ctx: ClassParseContext) {}

    suspend fun beforeMethod(ctx: MethodParseContext) {}

    suspend fun afterMethod(ctx: MethodParseContext) {}

    /**
     * Parameter hooks deliberately receive a [com.intellij.psi.PsiParameter]
     * instead of a `ResolvedParam` — parameter-dimension events are out of scope
     * for the `ResolvedMethod` unification.
     */
    suspend fun beforeParam(ctx: ParamParseContext) {}

    suspend fun afterParam(ctx: ParamParseContext) {}

    /** A single endpoint has been built (`EXPORT_AFTER` semantics); may mutate it. */
    suspend fun afterEndpoint(ctx: EndpointParseContext) {}
}
