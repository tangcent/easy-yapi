package com.itangcent.task

import com.google.inject.Inject
import com.itangcent.common.kit.KitUtils
import com.itangcent.idea.plugin.dialog.ApiDashboardDialog
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.task.Task.Companion.DONE
import com.itangcent.task.Task.Companion.INIT
import com.itangcent.task.Task.Companion.RUNNING
import com.itangcent.task.Task.Companion.TERMINAL
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater

interface Task {

    /**
     * Causes this task to begin execution
     */
    fun start()

    /**
     * Attempts to cancel execution of this task
     *
     * @return {@code false} if the task could not be terminal,
     * typically because it has already completed normally;
     * {@code true} otherwise
     */
    fun terminate(): Boolean

    /**
     * Waits if necessary for the task to complete
     */
    fun waitDone()

    /**
     * Returns {@code true} if this task was terminated before it completed
     * normally.
     *
     * @return {@code true} if this task was terminated before it completed
     */
    fun disposed(): Boolean

    /**
     * Returns {@code true} if this task completed.
     * @return {@code true} if this task completed
     */
    fun isDone(): Boolean

    /**
     * Returns {@code true} if this task is running.
     * @return {@code true} if this task is running
     */
    fun isRunning(): Boolean

    companion object {
        const val INIT = 0
        const val RUNNING = 1
        const val TERMINAL = 2
        const val DONE = 3
    }
}


class TaskManager {

    private val tasks = LinkedList<Task>()

    private val indexSeed = AtomicInteger(0)

    @Synchronized
    fun addTask(task: Task) {
        tasks.add(task)
    }

    fun currentIndex(): Int {
        return indexSeed.get()
    }

    fun nextIndex(): Int {
        return indexSeed.incrementAndGet()
    }

    @Synchronized
    fun terminateAll() {
        for (task in tasks) {
            task.terminate()
        }
    }

    @Synchronized
    fun waitDone() {
        tasks.forEach { it.waitDone() }
        tasks.clear()
    }
}

abstract class BaseTask(
    protected val actionContext: ActionContext,
    protected val taskManager: TaskManager
) : Task {

    init {
        actionContext.init(this)
    }

    @Inject
    protected lateinit var logger: Logger

    private var index: Int = taskManager.nextIndex()

    @Volatile
    private var status: Int = 0

    /**
     * @return task status
     */
    abstract fun doTask(): Int?

    override fun start() {
        addTaskToManager()

        if (switchStatus(RUNNING)) {
            try {
                val taskStatus = doTask() ?: DONE
                if (taskStatus != RUNNING) {
                    switchStatus(taskStatus)
                }
            } catch (e: Throwable) {
                complete()
                throw e
            }
        }
    }

    protected fun addTaskToManager() {
        synchronized(taskManager) {
            if (disposed()) {
                complete()
                return
            }
            taskManager.terminateAll()
            taskManager.waitDone()
            taskManager.addTask(this)
        }
    }

    fun complete() {
        status = DONE
    }

    override fun terminate(): Boolean {
        return switchStatus(TERMINAL)
    }

    /**
     * Waits if necessary for the task to complete
     */
    override fun waitDone() {
        for (i in 0..50) {
            if (isDone()) {
                return
            }
            Thread.sleep(100)
        }
        LOG.warn("wait task $this done timeout")
    }

    override fun disposed(): Boolean {
        return (status != INIT && status != RUNNING)
                || this.index < taskManager.currentIndex()
    }

    override fun isDone(): Boolean {
        return status == DONE
    }

    override fun isRunning(): Boolean {
        return status == RUNNING
    }

    protected fun switchStatus(next: Int): Boolean {
        var current = this.status
        while (current < next) {
            if (statusFieldUpdater.compareAndSet(this, current, next)) {
                return true
            }
            current = this.status
        }
        return false
    }

    companion object {
        val statusFieldUpdater: AtomicIntegerFieldUpdater<BaseTask> = AtomicIntegerFieldUpdater.newUpdater(
            BaseTask::class.java, "status"
        )
    }
}


abstract class AsyncTask(actionContext: ActionContext, taskManager: TaskManager) :
    BaseTask(actionContext, taskManager) {

    var runningFuture: Future<*>? = null

    override fun start() {

        addTaskToManager()

        runningFuture = actionContext.runAsync {
            if (switchStatus(RUNNING)) {
                try {
                    val taskStatus = doTask() ?: DONE
                    if (taskStatus != RUNNING) {
                        switchStatus(taskStatus)
                    }
                } catch (e: Throwable) {
                    complete()
                    throw e
                }
            }
            runningFuture = null
        }
    }

    override fun terminate(): Boolean {
        val ret = super.terminate()
        runningFuture?.let {
            KitUtils.safe {
                if (!it.isDone) {
                    it.cancel(true)
                }
            }
        }
        return ret
    }
}
private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(Task::class.java)