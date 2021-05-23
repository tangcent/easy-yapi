package com.itangcent.suv.http

import com.google.inject.Inject
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * Test case of [HttpClientProvider]
 */
internal abstract class HttpClientProviderTest : AdvancedContextTest() {

    @Inject
    private lateinit var httpClientProvider: HttpClientProvider

    abstract val httpClientProviderClass: KClass<out HttpClientProvider>

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(HttpClientProvider::class) { it.with(httpClientProviderClass) }
    }

    @Test
    fun buildHttpClient() {
        val response = httpClientProvider.getHttpClient()
                .get("https://www.apache.org/licenses/LICENSE-1.1")
                .call()
        assertEquals(200, response.code())
        logger.info("call LICENSE-1.1:[${response.code()}]\n${response.string()}")
    }
}