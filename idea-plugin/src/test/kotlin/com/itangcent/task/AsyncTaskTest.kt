package com.itangcent.task

import com.itangcent.common.utils.ThreadPoolUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.EasyBaseContextTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [AsyncTask]
 */
internal class AsyncTaskTest : EasyBaseContextTest() {

    private val taskManager = TaskManager()

    @AfterEach
    fun terminateAllTask() {
        taskManager.terminateAll()
    }

    @Test
    fun testSimpleTask() {
        //simple task
        val immediateTask = AsyncTaskImpl(actionContext, taskManager)
        immediateTask.start()
        immediateTask.waitDone()
        assertTrue(immediateTask.isDone())

        val task = AsyncTaskImpl(actionContext, taskManager, 1000)
        task.start()
        assertFalse(task.isDone())
        task.waitDone()
        assertTrue(task.isDone())
    }

    @Test
    fun testUncompletedTask() {
        val uncompletedTask = AsyncTaskImpl(actionContext, taskManager, null, Task.RUNNING)
        LOG.info("[testUncompletedTask]start uncompletedTask")
        uncompletedTask.start()
        LOG.info("[testUncompletedTask]wait uncompletedTask done")
        uncompletedTask.waitDone()
        LOG.info("[testUncompletedTask]uncompletedTask is done")
        assertFalse(uncompletedTask.disposed())

        val task = AsyncTaskImpl(actionContext, taskManager)
        LOG.info("[testUncompletedTask]start task")
        task.start()
        LOG.info("[testUncompletedTask]wait task done")
        task.waitDone()
        LOG.info("[testUncompletedTask]task is done")
        assertTrue(task.isDone())
        assertTrue(uncompletedTask.disposed())
    }

    @Test
    fun testComplete() {
        //simple task
        val task = AsyncTaskImpl(actionContext, taskManager)
        task.complete()
        assertTrue(task.disposed())
    }

    @Test
    fun testMultiThreadingTask() {

        val terminatedTask = AsyncTaskImpl(actionContext, taskManager, 20000)
        terminatedTask.start()
        terminatedTask.waitRunning()

        val task = AsyncTaskImpl(actionContext, taskManager)
        task.start()
        task.waitDone()
        assertTrue(task.isDone())
        assertTrue(terminatedTask.isDone())
    }

    @Test
    fun testTerminateBeforeStartTask() {
        val executorService = ThreadPoolUtils.createPool(10, AsyncTask::class.java)

        val task = AsyncTaskImpl(actionContext, taskManager, 1000)
        val terminatedTask = AsyncTaskImpl(actionContext, taskManager, 3000)
        executorService.submit {
            terminatedTask.start()
        }

        terminatedTask.waitRunning()

        task.start()
        assertTrue(task.isDone())
        terminatedTask.waitDone()
        assertTrue(terminatedTask.isDone())
        assertFalse(terminatedTask.isAborted() == true)
    }

    @Test
    fun testTerminatedTask() {
        val terminatedTask = AsyncTaskImpl(actionContext, taskManager, 20000)
        val thread = Thread {
            assertThrows<InterruptedException> {
                terminatedTask.start()
            }
        }
        thread.start()

        terminatedTask.waitRunning()

        Thread.sleep(100)
        thread.interrupt()
        terminatedTask.waitDone()
        assertFalse(terminatedTask.isAborted() == true)
    }

    private class AsyncTaskImpl(
        actionContext: ActionContext,
        taskManager: TaskManager,
        private val elapse: Long? = null,
        private val doTaskStatus: Int? = null
    ) : AsyncTask(
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

private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(AsyncTaskTest::class.java)