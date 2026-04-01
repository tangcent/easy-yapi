package com.itangcent.easyapi.testFramework

import com.intellij.openapi.project.Project
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

fun wrap(project: Project, block: ProjectWrapperBuilder.() -> Unit): Project {
    val builder = ProjectWrapperBuilder().apply(block)
    val injectedServices = builder.build()

    val handler = ProjectWrapperInvocationHandler(project, injectedServices)
    return Proxy.newProxyInstance(
        project.javaClass.classLoader,
        arrayOf(Project::class.java),
        handler
    ) as Project
}

class ProjectWrapperBuilder {

    private val services = linkedMapOf<Class<*>, Any>()

    fun <T : Any> replaceService(serviceClass: KClass<T>, instance: T) {
        services[serviceClass.java] = instance
    }

    internal fun build(): Map<Class<*>, Any> = services.toMap()
}

private class ProjectWrapperInvocationHandler(
    private val delegate: Project,
    private val services: Map<Class<*>, Any>
) : InvocationHandler {

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        val actualArgs = args ?: emptyArray()

        return when {
            method.name == "getService" && actualArgs.size == 1 && actualArgs[0] is Class<*> -> {
                val serviceClass = actualArgs[0] as Class<*>
                services[serviceClass] ?: method.invoke(delegate, *actualArgs)
            }

            method.name == "getServiceIfCreated" && actualArgs.size == 1 && actualArgs[0] is Class<*> -> {
                val serviceClass = actualArgs[0] as Class<*>
                services[serviceClass] ?: method.invoke(delegate, *actualArgs)
            }

            method.name == "equals" && actualArgs.size == 1 -> proxy === actualArgs[0]
            method.name == "hashCode" && actualArgs.isEmpty() -> System.identityHashCode(proxy)
            method.name == "toString" && actualArgs.isEmpty() -> "WrappedProject($delegate)"
            else -> method.invoke(delegate, *actualArgs)
        }
    }
}
