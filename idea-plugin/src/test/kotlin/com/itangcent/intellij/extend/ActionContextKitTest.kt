package com.itangcent.intellij.extend

import com.itangcent.mock.BaseContextTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals

/**
 * Test case ot [com.itangcent.intellij.extend.ActionContextKit]
 */
internal class ActionContextKitTest : BaseContextTest() {

    @Volatile
    private var x = 0

    @Test
    fun tryRunAsync() {
        null.tryRunAsync {
            x = 1
        }
        assertEquals(1, x)
        actionContext.tryRunAsync {
            x = 2
        }
        actionContext.waitComplete()
        assertEquals(2, x)
    }

    @Test
    fun callWithTimeout() {
        assertThrows<TimeoutException> {
            actionContext.callWithTimeout(500) {
                Thread.sleep(1000)
                logger.info("timeout")
                x = 1
            }
        }
        assertThrows<IllegalArgumentException> {
            actionContext.callWithTimeout(500) {
                throw IllegalArgumentException()
            }
        }
        assertEquals(2, actionContext.callWithTimeout(1000) {
            Thread.sleep(500)
            x = 2
            x
        })
        assertEquals(2, x)
    }
}