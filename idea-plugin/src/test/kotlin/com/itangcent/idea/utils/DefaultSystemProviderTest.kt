package com.itangcent.idea.utils

import com.google.inject.Inject
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test case of [DefaultSystemProvider]
 */
internal class DefaultSystemProviderTest : AdvancedContextTest() {

    @Inject
    private lateinit var systemProvider: SystemProvider

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SystemProvider::class) { it.with(DefaultSystemProvider::class) }
    }

    @Test
    fun testCurrentTimeMillis() {
        assertTrue(abs(systemProvider.currentTimeMillis() - System.currentTimeMillis()) < 5)
    }

    @Test
    fun testRunTime() {
        assertEquals(Runtime.getRuntime(), systemProvider.runtime())
    }
}