package com.itangcent.debug

import com.itangcent.common.utils.SystemUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.EasyBaseContextTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test case with [LoggerCollector]
 */
internal class LoggerCollectorTest : EasyBaseContextTest() {

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(Logger::class) { it.with(LoggerCollector::class) }
    }

    @Test
    fun testLog() {
        logger.debug("hello")
        logger.info("world")
        assertEquals("[DEBUG]\thello${SystemUtils.newLine()}" +
                "[INFO]\tworld${SystemUtils.newLine()}", LoggerCollector.getLog())
    }
}