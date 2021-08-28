package com.itangcent.task

import com.itangcent.common.utils.ThreadPoolUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.BaseContextTest
import com.itangcent.utils.WaitHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [BaseTask]
 */
internal class BaseTaskTest : BaseContextTest() {

    private val taskManager = TaskManager()

    @Test
    fun testSimpleTask() {
        //simple task
        val task = BaseTaskImpl(actionContext, taskManager)
        task.start()
        assertTrue(task.isDone())
    }

    @Test
    fun testUncompletedTask() {
        val uncompletedTask = BaseTaskImpl(actionContext, taskManager, null, Task.RUNNING)
        uncompletedTask.start()
        assertFalse(uncompletedTask.disposed())

        val task = BaseTaskImpl(actionContext, taskManager)
        task.start()
        assertTrue(task.isDone())
        assertTrue(uncompletedTask.disposed())
    }

    @Test
    fun testComplete() {
        //simple task
        val task = BaseTaskImpl(actionContext, taskManager)
        task.complete()
        assertTrue(task.disposed())
    }

    @Test
    fun testMultiThreadingTask() {
        val executorService = ThreadPoolUtils.createPool(10, BaseTask::class.java)

        val terminatedTask = BaseTaskImpl(actionContext, taskManager, 20000)
        executorService.submit {
            terminatedTask.start()
        }
        WaitHelper.waitUtil(10000) {
            terminatedTask.isRunning()
        }

        val task = BaseTaskImpl(actionContext, taskManager, 1000)
        task.start()
        assertTrue(task.isDone())
        assertTrue(terminatedTask.isDone())
    }

    @Test
    fun testTerminateBeforeStartTask() {
        val executorService = ThreadPoolUtils.createPool(10, BaseTask::class.java)

        val task = BaseTaskImpl(actionContext, taskManager, 1000)
        val terminatedTask = BaseTaskImpl(actionContext, taskManager, 3000)
        executorService.submit {
            terminatedTask.start()
        }

        while (!terminatedTask.isRunning()) {
            Thread.sleep(100)
        }

        task.start()
        assertTrue(task.isDone())
        terminatedTask.waitDone()
        assertTrue(terminatedTask.isDone())
        assertFalse(terminatedTask.isAborted() == true)
    }

    @Test
    fun testTerminatedTask() {
        val terminatedTask = BaseTaskImpl(actionContext, taskManager, 20000)
        val thread = Thread {
            assertThrows<InterruptedException> {
                terminatedTask.start()
            }
        }
        thread.start()

        while (!terminatedTask.isRunning()) {
            Thread.sleep(100)
        }

        Thread.sleep(100)
        thread.interrupt()
        terminatedTask.waitDone()
        assertFalse(terminatedTask.isAborted() == true)
    }

    private class BaseTaskImpl(
        actionContext: ActionContext,
        taskManager: TaskManager,
        private val elapse: Long? = null,
        private val doTaskStatus: Int? = null
    ) : BaseTask(
        actionContext,
        taskManager
    ) {
        private var aborted: Boolean? = null

        override fun doTask(): Int? {
            elapse?.let {
                val gap = java.lang.Long.min(it / 8, 200L)
                for (i in 0..(elapse / gap)) {
                    Thread.sleep(gap)
                    if (disposed()) {
                        aborted = true
                        return Task.DONE
                    }
                }
            }
            return doTaskStatus
        }

        fun isAborted(): Boolean? {
            return aborted
        }

    }
}