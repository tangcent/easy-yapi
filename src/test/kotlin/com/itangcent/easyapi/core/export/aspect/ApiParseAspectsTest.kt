package com.itangcent.easyapi.core.export.aspect

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.internal.PluginInfo
import com.itangcent.easyapi.core.psi.type.ResolvedMethod
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.mockito.kotlin.mock

/**
 * Verifies the dispatch semantics of [ApiParseAspects] with fake aspects
 * registered through the real `apiParseAspect` extension point.
 *
 * Covered:
 * - `before` hooks run in ascending `order`, the block runs between before/after,
 *   and `after` hooks also run in ascending `order`;
 * - a throwing block still runs the `after` hooks (they live in `finally`);
 * - a throwing `before` prevents the block and the `after` hooks from running;
 * - `isEnabled` / `appliesTo` filter aspects out;
 * - `afterEndpoint` runs in ascending `order`.
 */
class ApiParseAspectsTest : EasyApiLightCodeInsightFixtureTestCase() {

    /** Created lazily: [project] is not available while the test instance is constructed. */
    private val aspects get() = ApiParseAspects(project)

    private val trace = mutableListOf<String>()

    private fun register(aspect: ApiParseAspect) {
        project.extensionArea
            .getExtensionPoint<ApiParseAspect>("${PluginInfo.PLUGIN_ID}.apiParseAspect")
            .registerExtension(aspect, testRootDisposable)
    }

    private fun registerAll(vararg aspect: ApiParseAspect) = aspect.forEach { register(it) }

    private fun endpointContext(): EndpointParseContext = EndpointParseContext(
        endpoint = ApiEndpoint(metadata = HttpMetadata("/test", HttpMethod.GET)),
        frameworkName = "SpringMVC",
        resolvedMethod = ResolvedMethod("m", mock<PsiMethod>())
    )

    private class RecordingAspect(
        private val trace: MutableList<String>,
        override val order: Int,
        private val label: String,
        private val enabled: Boolean = true,
        private val frameworks: Set<String> = setOf("SpringMVC"),
        private val thrownOnBefore: Throwable? = null
    ) : ApiParseAspect {

        override fun isEnabled(): Boolean = enabled

        override fun appliesTo(frameworkName: String): Boolean = frameworkName in frameworks

        override suspend fun beforeClass(ctx: ClassParseContext) {
            trace += "$label.beforeClass"
            thrownOnBefore?.let { throw it }
        }

        override suspend fun afterClass(ctx: ClassParseContext) {
            trace += "$label.afterClass"
        }

        override suspend fun beforeMethod(ctx: MethodParseContext) {
            trace += "$label.beforeMethod"
        }

        override suspend fun afterMethod(ctx: MethodParseContext) {
            trace += "$label.afterMethod"
        }

        override suspend fun beforeParam(ctx: ParamParseContext) {
            trace += "$label.beforeParam"
        }

        override suspend fun afterParam(ctx: ParamParseContext) {
            trace += "$label.afterParam"
        }

        override suspend fun afterEndpoint(ctx: EndpointParseContext) {
            trace += "$label.afterEndpoint"
        }
    }

    fun testBeforeAndAfterRunInAscendingOrder() = runTest {
        registerAll(
            RecordingAspect(trace, order = 1, label = "b"),
            RecordingAspect(trace, order = 0, label = "a")
        )

        val result = aspects.aroundClass("SpringMVC", mock<PsiClass>()) { "block-ran" }

        assertEquals("block-ran", result)
        assertEquals(
            "before (asc) → block → after (asc)",
            listOf("a.beforeClass", "b.beforeClass", "a.afterClass", "b.afterClass"),
            trace
        )
    }

    fun testBlockExceptionStillRunsAfterHooks() = runTest {
        registerAll(
            RecordingAspect(trace, order = 0, label = "a"),
            RecordingAspect(trace, order = 1, label = "b")
        )

        var thrown: Throwable? = null
        try {
            aspects.aroundClass("SpringMVC", mock<PsiClass>()) { throw IllegalStateException("boom") }
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertNotNull("Block exception must propagate", thrown)
        assertEquals(
            "after hooks must still run when the block throws",
            listOf("a.beforeClass", "b.beforeClass", "a.afterClass", "b.afterClass"),
            trace
        )
    }

    fun testBeforeExceptionPreventsBlockAndAfterHooks() = runTest {
        registerAll(
            RecordingAspect(trace, order = 0, label = "a", thrownOnBefore = IllegalStateException("boom")),
            RecordingAspect(trace, order = 1, label = "b")
        )

        var thrown: Throwable? = null
        try {
            aspects.aroundClass("SpringMVC", mock<PsiClass>()) { trace += "block" }
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertNotNull("before exception must propagate", thrown)
        assertEquals("before exception aborts remaining before hooks and after hooks", listOf("a.beforeClass"), trace)
    }

    fun testDisabledAndNonMatchingAspectsAreFilteredOut() = runTest {
        registerAll(
            RecordingAspect(trace, order = 0, label = "disabled", enabled = false),
            RecordingAspect(trace, order = 1, label = "otherFramework", frameworks = setOf("JaxRs"))
        )

        val result = aspects.aroundClass("SpringMVC", mock<PsiClass>()) { "block-ran" }

        assertEquals("block-ran", result)
        assertTrue("No aspect should have been invoked: $trace", trace.isEmpty())
    }

    fun testAfterEndpointRunsInAscendingOrder() = runTest {
        registerAll(
            RecordingAspect(trace, order = 1, label = "b"),
            RecordingAspect(trace, order = 0, label = "a")
        )

        aspects.afterEndpoint(endpointContext())

        assertEquals(listOf("a.afterEndpoint", "b.afterEndpoint"), trace)
    }

    fun testMethodAndParamHooksAreDispatched() = runTest {
        register(RecordingAspect(trace, order = 0, label = "a"))
        val methodCtx = MethodParseContext(ResolvedMethod("m", mock<PsiMethod>()), "SpringMVC")

        val methodResult = aspects.aroundMethod(methodCtx) { "method-ran" }
        assertEquals("method-ran", methodResult)

        val paramResult = aspects.aroundParam("SpringMVC", mock<PsiParameter>()) { "param-ran" }
        assertEquals("param-ran", paramResult)

        assertEquals(
            listOf("a.beforeMethod", "a.afterMethod", "a.beforeParam", "a.afterParam"),
            trace
        )
    }
}
