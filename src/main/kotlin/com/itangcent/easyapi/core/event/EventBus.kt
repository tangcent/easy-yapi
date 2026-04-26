package com.itangcent.easyapi.core.event

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import com.itangcent.easyapi.logging.IdeaConsole
import com.itangcent.easyapi.logging.IdeaConsoleProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class EventBus(private val project: Project) : Disposable {

    private val handlers = ConcurrentHashMap<String, CopyOnWriteArrayList<suspend () -> Unit>>()
    private val oneTimeHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<suspend () -> Unit>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var messageBusConnection: MessageBusConnection? = null

    private val console: IdeaConsole by lazy {
        IdeaConsoleProvider.getInstance(project).getConsole()
    }

    private fun ensureConnected() {
        if (messageBusConnection != null) return
        if (project.isDisposed) return
        synchronized(this) {
            if (messageBusConnection != null) return
            if (project.isDisposed) return
            adaptTopic(
                ActionCompletedTopic.TOPIC
            ) { bus ->
                object : ActionCompletedTopic {
                    override fun onActionCompleted() {
                        bus.fireAsync(EventKeys.ON_COMPLETED)
                    }
                }
            }
        }
    }

    fun register(key: String, handler: suspend () -> Unit) {
        ensureConnected()
        handlers.computeIfAbsent(key) { CopyOnWriteArrayList() }.add(handler)
    }

    fun registerOnce(key: String, handler: suspend () -> Unit) {
        ensureConnected()
        oneTimeHandlers.computeIfAbsent(key) { CopyOnWriteArrayList() }.add(handler)
    }

    private suspend fun fire(key: String) {
        val persistent = handlers[key] ?: emptyList()
        val oneTime = oneTimeHandlers.remove(key) ?: emptyList()

        for (handler in persistent) {
            try {
                handler()
            } catch (e: Exception) {
                console.warn("Event handler failed for key=$key", e)
            }
        }

        for (handler in oneTime) {
            try {
                handler()
            } catch (e: Exception) {
                console.warn("One-time event handler failed for key=$key", e)
            }
        }
    }

    private fun fireAsync(key: String) {
        ensureConnected()
        scope.launch { fire(key) }
    }

    private fun <L : Any> adaptTopic(topic: Topic<L>, listenerFactory: (EventBus) -> L) {
        val connection = project.messageBus.connect(this)
        val listener = listenerFactory(this)
        connection.subscribe(topic, listener)
        messageBusConnection = connection
    }

    override fun dispose() {
        scope.cancel()
        messageBusConnection?.disconnect()
        messageBusConnection = null
        handlers.clear()
        oneTimeHandlers.clear()
    }

    companion object {
        fun getInstance(project: Project): EventBus = project.service()
    }
}
