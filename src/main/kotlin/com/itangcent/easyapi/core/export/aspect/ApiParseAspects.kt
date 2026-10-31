package com.itangcent.easyapi.core.export.aspect

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.core.internal.PluginInfo
import com.itangcent.easyapi.core.logging.IdeaLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dispatches the API parse lifecycle to the registered [ApiParseAspect]s.
 *
 * The aspect list is read from the extension point on **every** dispatch call
 * (never cached, never passed down as a snapshot): implementations number only
 * a handful, while dynamic registration in tests and settings toggles must take
 * effect on the next call without invalidation bookkeeping.
 *
 * Semantics:
 * - all `beforeXxx` hooks run outside the `try`, then the block, then all
 *   `afterXxx` hooks in a `finally` block;
 * - `afterXxx` hooks run in ascending [ApiParseAspect.order];
 * - a throwing `beforeXxx` prevents the `afterXxx` hooks from running;
 * - a throwing `afterXxx` aborts the remaining hooks and propagates.
 */
@Service(Service.Level.PROJECT)
class ApiParseAspects(private val project: Project) : IdeaLog {

    /**
     * Warns once per project when the extension point is missing. Concurrent
     * scans can hit the missing-EP path from several threads at once, so the
     * flag must be flipped with CAS rather than check-then-set.
     */
    private val warnedMissingEp = AtomicBoolean(false)

    /**
     * Reads the EP, filters by [ApiParseAspect.isEnabled] / [ApiParseAspect.appliesTo]
     * and sorts by [ApiParseAspect.order].
     *
     * Only the EP lookup's [IllegalArgumentException] is treated as "missing EP".
     * `extensionList` is read **outside** the catch block so an extension
     * instantiation failure propagates instead of degrading to an empty list.
     */
    private fun aspects(frameworkName: String): List<ApiParseAspect> {
        val extensionPoint = try {
            project.extensionArea.getExtensionPoint<ApiParseAspect>(EP.name)
        } catch (e: IllegalArgumentException) {
            warnMissingEpOnce()
            return emptyList()
        }
        return extensionPoint.extensionList
            .filter { it.isEnabled() && it.appliesTo(frameworkName) }
            .sortedBy { it.order }
    }

    private fun warnMissingEpOnce() {
        if (warnedMissingEp.compareAndSet(false, true)) {
            LOG.warn(
                "API parse aspect extension point '${EP.name}' is missing; " +
                    "all lifecycle rule events will be skipped"
            )
        }
    }

    suspend fun <T> aroundClass(
        frameworkName: String,
        psiClass: PsiClass,
        block: suspend (ClassParseContext) -> T
    ): T {
        val ctx = ClassParseContext(psiClass, frameworkName)
        val aspects = aspects(frameworkName)
        aspects.forEach { it.beforeClass(ctx) }
        try {
            return block(ctx)
        } finally {
            aspects.forEach { it.afterClass(ctx) }
        }
    }

    suspend fun <T> aroundMethod(
        ctx: MethodParseContext,
        block: suspend (MethodParseContext) -> T
    ): T {
        val aspects = aspects(ctx.frameworkName)
        aspects.forEach { it.beforeMethod(ctx) }
        try {
            return block(ctx)
        } finally {
            aspects.forEach { it.afterMethod(ctx) }
        }
    }

    suspend fun <T> aroundParam(
        frameworkName: String,
        parameter: PsiParameter,
        block: suspend (ParamParseContext) -> T
    ): T {
        val ctx = ParamParseContext(parameter, frameworkName)
        val aspects = aspects(frameworkName)
        aspects.forEach { it.beforeParam(ctx) }
        try {
            return block(ctx)
        } finally {
            aspects.forEach { it.afterParam(ctx) }
        }
    }

    suspend fun afterEndpoint(ctx: EndpointParseContext) {
        val aspects = aspects(ctx.frameworkName)
        aspects.forEach { it.afterEndpoint(ctx) }
    }

    companion object {
        private val EP: ExtensionPointName<ApiParseAspect> =
            ExtensionPointName.create("${PluginInfo.PLUGIN_ID}.apiParseAspect")

        fun getInstance(project: Project): ApiParseAspects = project.service()
    }
}
