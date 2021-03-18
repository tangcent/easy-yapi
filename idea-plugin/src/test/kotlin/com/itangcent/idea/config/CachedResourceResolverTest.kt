package com.itangcent.idea.config

import com.google.inject.Inject
import com.itangcent.AdvancedContextTest
import com.itangcent.intellij.config.resource.ResourceResolver
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
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
        val content = resource.content
        logger.info("load LICENSE-2.0:\n$content")
        assertNotNull(content)
    }
}