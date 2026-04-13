package com.itangcent.easyapi.core.event

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.itangcent.easyapi.logging.IdeaConsole
import com.itangcent.easyapi.logging.IdeaConsoleProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class EventBus(private val project: Project) {

    private val handlers = ConcurrentHashMap<String, CopyOnWriteArrayList<suspend () -> Unit>>()
    private val oneTimeHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<suspend () -> Unit>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val console: IdeaConsole by lazy {
        IdeaConsoleProvider.getInstance(project).getConsole()
    }

    init {
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

    fun register(key: String, handler: suspend () -> Unit) {
        handlers.computeIfAbsent(key) { CopyOnWriteArrayList() }.add(handler)
    }

    fun registerOnce(key: String, handler: suspend () -> Unit) {
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
        scope.launch { fire(key) }
    }

    private fun <L : Any> adaptTopic(topic: Topic<L>, listenerFactory: (EventBus) -> L) {
        val connection = project.messageBus.connect()
        val listener = listenerFactory(this)
        connection.subscribe(topic, listener)
    }

    companion object {
        fun getInstance(project: Project): EventBus = project.service()
    }
}
