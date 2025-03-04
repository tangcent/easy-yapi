package com.itangcent.debug

import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.BaseContextTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test case with [LoggerCollector]
 */
internal class LoggerCollectorTest : BaseContextTest() {

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(Logger::class) { it.with(LoggerCollector::class) }
    }

    @Test
    fun testLog() {
        logger.debug("hello")
        logger.info("world")
        assertEquals(
            "[DEBUG]\thello\n" +
                    "[INFO]\tworld\n", LoggerCollector.getLog()
        )
    }
}