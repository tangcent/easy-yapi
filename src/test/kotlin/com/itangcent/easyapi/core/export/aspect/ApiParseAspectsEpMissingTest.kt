package com.itangcent.easyapi.core.export.aspect

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.psi.type.ResolvedMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Verifies the missing-extension-point degradation of [ApiParseAspects].
 *
 * `project.extensionArea.getExtensionPoint(...)` throws [IllegalArgumentException]
 * when the `apiParseAspect` EP is not registered — which happens in lightweight
 * unit tests that run without `plugin.xml`. The dispatcher must treat that as
 * "no aspects" and let the wrapped block run normally.
 *
 * Plain JUnit (no IDE fixture): a mocked [com.intellij.openapi.project.Project]
 * whose `getExtensionArea()` throws is enough to exercise the branch.
 */
class ApiParseAspectsEpMissingTest {

    private fun projectWithoutAspects() = mock<com.intellij.openapi.project.Project> {
        on { extensionArea } doThrow IllegalArgumentException("Missing extension point")
    }

    @Test
    fun testAroundClassDegradesToEmptyAspectList() = runBlocking {
        val aspects = ApiParseAspects(projectWithoutAspects())
        val result = aspects.aroundClass("SpringMVC", mock<PsiClass>()) { "block-ran" }
        assertEquals("block-ran", result)
    }

    @Test
    fun testAroundMethodDegradesToEmptyAspectList() = runBlocking {
        val aspects = ApiParseAspects(projectWithoutAspects())
        val ctx = MethodParseContext(ResolvedMethod("m", mock<PsiMethod>()), "SpringMVC")
        val result = aspects.aroundMethod(ctx) { "block-ran" }
        assertEquals("block-ran", result)
    }

    @Test
    fun testAroundParamDegradesToEmptyAspectList() = runBlocking {
        val aspects = ApiParseAspects(projectWithoutAspects())
        val result = aspects.aroundParam("SpringMVC", mock<PsiParameter>()) { "block-ran" }
        assertEquals("block-ran", result)
    }

    @Test
    fun testAfterEndpointDegradesToNoOp() = runBlocking {
        val aspects = ApiParseAspects(projectWithoutAspects())
        val ctx = EndpointParseContext(
            endpoint = ApiEndpoint(metadata = HttpMetadata("/test", HttpMethod.GET)),
            frameworkName = "SpringMVC",
            resolvedMethod = ResolvedMethod("m", mock<PsiMethod>())
        )
        aspects.afterEndpoint(ctx)
    }

    /**
     * The missing-EP warning is guarded by a CAS so concurrent scans log it once,
     * not once per dispatcher call.
     */
    @Test
    fun testMissingEpWarnsOnceUnderConcurrentDispatch() = runBlocking {
        val logger = mock<Logger>()
        val aspects = spy(ApiParseAspects(projectWithoutAspects())) {
            on { LOG } doReturn logger
        }

        coroutineScope {
            repeat(16) { index ->
                launch(Dispatchers.Default) {
                    aspects.aroundClass("SpringMVC", mock<PsiClass>()) { "block-$index" }
                }
            }
        }

        verify(logger, times(1)).warn(any<String>())
    }
}
