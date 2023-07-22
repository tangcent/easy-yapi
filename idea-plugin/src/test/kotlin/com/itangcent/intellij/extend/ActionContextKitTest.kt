package com.itangcent.intellij.extend

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiMethod
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ThreadFlag
import com.itangcent.mock.BaseContextTest
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals

/**
 * Test case of [com.itangcent.intellij.extend.ActionContextKit]
 */
internal class ActionContextKitTest : BaseContextTest() {

    @Test
    fun `test tryRunAsync`() {
        var ran = false

        // Test with a null context
        null.tryRunAsync {
            Thread.sleep(500)
            ran = true
        }
        assertTrue(ran)

        // Test with a no-null context
        ran = false
        actionContext.tryRunAsync {
            Thread.sleep(500)
            ran = true
        }
        assertFalse(ran)
        Thread.sleep(1000)
        assertTrue(ran)
    }

    @Test
    fun `test callWithTimeout`() {
        // Test with a long-running task
        assertThrows<TimeoutException> {
            actionContext.callWithTimeout(1000) {
                Thread.sleep(2000)
                "Hello world"
            }
        }

        // Test with a short task
        val result2 = actionContext.callWithTimeout(1000) {
            "Hello world"
        }
        assertEquals("Hello world", result2) // The result should be "Hello world"

        assertThrows<TimeoutException> {
            actionContext.callWithTimeout<Unit>(500) {
                Thread.sleep(1000)
                throw IllegalArgumentException()
            }
        }

        assertThrows<IllegalArgumentException> {
            actionContext.callWithTimeout<Unit>(1500) {
                Thread.sleep(1000)
                throw IllegalArgumentException()
            }
        }
    }

    @Test
    fun `test withBoundary`() {
        var ran = false

        actionContext.withBoundary {
            actionContext.runAsync {
                ran = true
            }
        }

        assertTrue(ran)
    }

    @Test
    fun `test notReentrant`() {
        var ran = false

        actionContext.notReentrant("test") {
            ran = true
        }
        assertTrue(ran)

        // Test with a recursive call
        ran = false
        actionContext.notReentrant("test") {
            actionContext.notReentrant("test") {
                ran = true
            }
        }
        assertFalse(ran) // The inner action should not have run because it was recursive
    }

    @Test
    fun `test callWithBoundary`() {
        val times = AtomicInteger()
        val result = actionContext.callWithBoundary {
            repeat(10) {
                actionContext.runAsync {
                    Thread.sleep(100)
                    times.getAndIncrement()
                }
            }
            times.get()
        }!!
        assertTrue(10 > result)
        assertEquals(10, times.get())
    }

    @Test
    fun `test callWithBoundary with error`() {
        assertDoesNotThrow {
            actionContext.callWithBoundary<Any?> {
                throw IllegalArgumentException()
            }
        }
    }

    @Test
    fun `test withBoundary with timeout`() {
        var ran = false

        actionContext.withBoundary(1000) {
            Thread.sleep(500)
            ran = true
        }
        assertTrue(ran)

        ran = false
        actionContext.withBoundary(1000) {
            actionContext.runAsync {
                Thread.sleep(500)
                ran = true
            }
        }
        assertTrue(ran)

        // Test with a long-running sync task
        ran = false
        actionContext.withBoundary(1000) {
            Thread.sleep(2000)
            ran = true
        }
        assertTrue(ran)

        // Test with a long-running async task
        ran = false
        actionContext.withBoundary(1000) {
            actionContext.runAsync {
                Thread.sleep(2000)
                ran = true
            }
        }
        assertFalse(ran)
    }

    @Test
    fun `test withBoundary with error`() {
        assertThrows<IllegalArgumentException> {
            actionContext.withBoundary {
                throw IllegalArgumentException()
            }
        }
        assertDoesNotThrow {
            actionContext.withBoundary(1000) {
                throw IllegalArgumentException()
            }
        }
    }

    @Test
    fun `test runWithContext`() {
        var inContext = false

        actionContext.runWithContext {
            inContext = ActionContext.getContext() != null
        }
        assertTrue(inContext)

        // Test with no context
        inContext = false
        actionContext.withBoundary {
            thread {
                actionContext.runWithContext {
                    inContext = ActionContext.getContext() != null
                }
            }.join()
        }
        assertTrue(inContext) // The action should have run asynchronously because there was no context
    }

    @Test
    fun `test runInNormalThread`() = actionContext.withBoundary {
        actionContext.runInNormalThread {
            assertEquals(ThreadFlag.ASYNC.value, ActionContext.getFlag())
        }
        actionContext.runInSwingUI {
            actionContext.runInNormalThread {
                assertEquals(ThreadFlag.ASYNC.value, ActionContext.getFlag())
            }
        }
        actionContext.runInNormalThread {
            actionContext.runInNormalThread {
                assertEquals(ThreadFlag.ASYNC.value, ActionContext.getFlag())
            }
        }
    }

}

internal class PsiActionContextKitTest : PluginContextLightCodeInsightFixtureTestCase() {

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        builder.bindInstance(DataContext::class, mock())
    }

    fun `test findCurrentMethod`() {
        val psiElement = mock<PsiMethod> {
            on(it.context).thenReturn(it)
        }
        actionContext.cache(CommonDataKeys.PSI_ELEMENT.name, psiElement)
        kotlin.test.assertEquals(psiElement, actionContext.findCurrentMethod())
    }
}