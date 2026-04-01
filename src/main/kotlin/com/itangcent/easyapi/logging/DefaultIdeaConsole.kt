package com.itangcent.easyapi.logging

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.itangcent.easyapi.core.threading.swing
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Default implementation of [IdeaConsole] that outputs to an IntelliJ tool window.
 *
 * Creates and manages a "EasyAPI" tool window at the bottom of the IDE that displays
 * log messages with different severity levels. The console view is lazily initialized
 * on first log output.
 *
 * ## Features
 * - Thread-safe log queue with async processing
 * - Color-coded output based on log level
 * - Automatic tool window creation and management
 * - Swing thread safety for UI operations
 *
 * @see IdeaConsole for the interface
 * @see ConfigurableIdeaConsole for settings-aware wrapper
 */
class DefaultIdeaConsole(
    private val project: Project,
) : IdeaConsole {

    companion object {
        const val WINDOW_ID: String = "EasyAPI"
    }

    private val logQueue = ConcurrentLinkedQueue<Pair<LogLevel?, String>>()
    private val processing = AtomicBoolean(false)
    private var consoleInitialized = false

    private val consoleView: ConsoleView by lazy {
        val console = TextConsoleBuilderFactory.getInstance()
            .createBuilder(project)
            .console
        console
    }

    private suspend fun ensureConsoleInitialized() {
        if (consoleInitialized) return
        consoleInitialized = true

        swing {
            val toolWindowManager = ToolWindowManager.getInstance(project)
            var toolWindow = toolWindowManager.getToolWindow(WINDOW_ID)
            if (toolWindow == null) {
                toolWindow = toolWindowManager.registerToolWindow(WINDOW_ID) {
                    anchor = ToolWindowAnchor.BOTTOM
                    canCloseContent = false
                }
                val content = toolWindow.contentManager.factory.createContent(
                    consoleView.component, "", false
                )
                toolWindow.contentManager.addContent(content)
            } else {
                val content = toolWindow.contentManager.getContent(0)
                if (content != null) {
                    content.component = consoleView.component
                } else {
                    val newContent = toolWindow.contentManager.factory.createContent(
                        consoleView.component,
                        "",
                        false
                    )
                    toolWindow.contentManager.addContent(newContent)
                }
            }
            toolWindow.show()
        }
    }

    override fun println(msg: String) {
        print("$msg\n")
    }

    override fun print(msg: String) {
        enqueue(null, msg)
    }

    override fun trace(msg: String) {
        enqueue(LogLevel.TRACE, msg)
    }

    override fun debug(msg: String) {
        enqueue(LogLevel.DEBUG, msg)
    }

    override fun info(msg: String) {
        enqueue(LogLevel.INFO, msg)
    }

    override fun warn(msg: String, t: Throwable?) {
        val fullMsg = if (t != null) "$msg\n${t.stackTraceToString()}" else msg
        enqueue(LogLevel.WARN, fullMsg)
    }

    override fun error(msg: String, t: Throwable?) {
        val fullMsg = if (t != null) "$msg\n${t.stackTraceToString()}" else msg
        enqueue(LogLevel.ERROR, fullMsg)
    }

    private fun enqueue(level: LogLevel?, msg: String) {
        logQueue.add(level to msg)
        tryFlushLogs()
    }

    private fun tryFlushLogs() {
        if (processing.compareAndSet(false, true)) {
            flushLogs()
        }
    }

    private fun flushLogs() {
        GlobalScope.launch {
            swing {
                ensureConsoleInitialized()
                generateSequence { logQueue.poll() }
                    .forEach { (level, log) ->
                        printLog(level, log)
                    }
                processing.set(false)
                if (logQueue.isNotEmpty()) {
                    tryFlushLogs()
                }
            }
        }
    }

    private fun printLog(level: LogLevel?, log: String) {
        if (level == null) {
            consoleView.print(log, ConsoleViewContentType.SYSTEM_OUTPUT)
            return
        }
        val contentType = when (level) {
            LogLevel.TRACE -> ConsoleViewContentType.LOG_DEBUG_OUTPUT
            LogLevel.DEBUG -> ConsoleViewContentType.LOG_DEBUG_OUTPUT
            LogLevel.INFO -> ConsoleViewContentType.LOG_INFO_OUTPUT
            LogLevel.WARN -> ConsoleViewContentType.LOG_WARNING_OUTPUT
            LogLevel.ERROR -> ConsoleViewContentType.LOG_ERROR_OUTPUT
        }
        consoleView.print("[${level.name}]\t$log\n", contentType)
    }
}
