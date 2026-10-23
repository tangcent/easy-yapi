package com.itangcent.easyapi.core.export

import com.itangcent.easyapi.channel.spi.ChannelOption
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [ExportOptions]: the checks of the options a headless export call passes.
 */
class ExportOptionsTest {

    /** Names are compared trimmed, so two spellings of one name are one option given twice. */
    @Test
    fun testNameGivenTwiceOnceTrimmedIsOneProblem() {
        val accepted = listOf(ChannelOption("token", "A secret token."))

        val problems = ExportOptions.problems(mapOf("token" to "first-value", " token" to "second-value"), accepted)

        assertEquals("one problem, naming the option, never a value", listOf("option 'token' is given more than once"), problems)
    }
}
