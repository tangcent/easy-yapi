package com.itangcent.spi

import com.google.inject.Provider
import com.itangcent.intellij.context.ActionContext
import com.itangcent.utils.findGenericType
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

/**
 * Abstract base class for SPI bean providers.
 * This class provides common functionality for both SpiSingleBeanProvider and SpiCompositeBeanProvider.
 */
abstract class AbstractSpiBeanProvider<T : Any> : Provider<T> {

    // Get the KClass<T> from the generic type parameter
    @Suppress("UNCHECKED_CAST")
    protected val kClass: KClass<T> by lazy {
        this::class.java.findGenericType(AbstractSpiBeanProvider::class.java)
    }

    /**
     * Load the bean(s) from the SPI registry.
     * Subclasses should implement this to load either a single bean or a composite of beans.
     */
    protected abstract fun loadBean(actionContext: ActionContext, kClass: KClass<T>): T

    @Suppress("UNCHECKED_CAST")
    override fun get(): T {
        val context = ActionContext.getContext()
        return if (context != null) {
            // ActionContext is prepared, load the bean directly
            loadBean(context, kClass)
        } else {
            // ActionContext is not prepared, create a proxy
            createLazyLoadingProxy()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createLazyLoadingProxy(): T {
        return Proxy.newProxyInstance(
            kClass.java.classLoader,
            arrayOf(kClass.java),
            LazyLoadingInvocationHandler(kClass)
        ) as T
    }

    /**
     * An InvocationHandler that lazily loads the actual bean when a method is invoked.
     * It checks for ActionContext availability at method invocation time.
     */
    private inner class LazyLoadingInvocationHandler(
        private val kClass: KClass<T>
    ) : InvocationHandler {

        @Suppress("UNCHECKED_CAST")
        private val delegate: T by lazy {
            val context = ActionContext.getContext()
            if (context == null) {
                throw IllegalStateException("ActionContext is not prepared when attempting to use ${kClass.qualifiedName}")
            }
            loadBean(context, kClass)
        }

        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            // Invoke the method on the actual delegate
            return if (args == null) {
                method.invoke(delegate)
            } else {
                method.invoke(delegate, *args)
            }
        }
    }
} 