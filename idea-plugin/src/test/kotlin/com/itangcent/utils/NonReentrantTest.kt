package com.itangcent.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.concurrent.thread

class NonReentrantTest {

    @Test
    fun testCall() {
        assertThrows<NonReentrantException> {
            NonReentrant.call("test") {
                call()
            }
        }
        assertEquals("yes", call())

        val threadA = thread {
            NonReentrant.call("test") {
                Thread.sleep(1000)
            }
        }
        val threadB = thread {
            NonReentrant.call("test") {
                Thread.sleep(1000)
            }
        }
        threadA.join()
        threadB.join()
    }

    private fun call() = NonReentrant.call("test") {
        "yes"
    }
}