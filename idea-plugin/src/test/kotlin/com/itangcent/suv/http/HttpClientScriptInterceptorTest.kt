package com.itangcent.suv.http

import com.itangcent.http.HttpClient
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.AdvancedContextTest
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Test for [HttpClientScriptInterceptor]
 *
 * @author tangcent
 * @date 2024/05/11
 */
class HttpClientScriptInterceptorTest : AdvancedContextTest() {

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(HttpClientProvider::class) { it.with(HttpClientProviderImpl::class) }
    }

    open class HttpClientProviderImpl : HttpClientProvider {
        override fun getHttpClient(): HttpClient = mock()

        // keep it open for test
        open fun otherMethod(): String = "otherMethod"
    }

    @Test
    fun testGetHttpClient() {
        val httpClientProvider = actionContext.instance(HttpClientProvider::class)
        assertNotNull(httpClientProvider)
        assertIs<HttpClientProviderImpl>(httpClientProvider)

        val httpClient = httpClientProvider.getHttpClient()
        assertNotNull(httpClient)
        assertIs<HttpClientScriptInterceptor.HttpClientWrapper>(httpClient)
    }

    @Test
    fun testOtherMethod() {
        val httpClientProvider = actionContext.instance(HttpClientProvider::class)
        assertNotNull(httpClientProvider)
        assertIs<HttpClientProviderImpl>(httpClientProvider)

        val res = httpClientProvider.otherMethod()
        assertNotNull(res)
        assertEquals("otherMethod", res)
    }
}