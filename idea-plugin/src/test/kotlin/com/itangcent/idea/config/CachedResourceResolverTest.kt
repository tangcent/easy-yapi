package com.itangcent.idea.config

import com.google.inject.Inject
import com.itangcent.intellij.config.resource.ResourceResolver
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import java.net.SocketTimeoutException
import kotlin.test.assertNotNull

/**
 * Test case of [CachedResourceResolver]
 */
@DisabledOnOs(OS.WINDOWS)
internal class CachedResourceResolverTest : AdvancedContextTest() {

    @Inject
    private lateinit var cachedResourceResolver: CachedResourceResolver

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(ResourceResolver::class) {
            it.with(CachedResourceResolver::class)
        }
    }

    @Test
    fun testCachedResource() {
        val resource = cachedResourceResolver.resolve("http://www.apache.org/licenses/LICENSE-2.0")
        try {
            val content = resource.content
            logger.info("load LICENSE-2.0:\n$content")
            assertNotNull(content)
        } catch (e: SocketTimeoutException) {
            logger.warn("failed connect apache.org")
        }
    }
}