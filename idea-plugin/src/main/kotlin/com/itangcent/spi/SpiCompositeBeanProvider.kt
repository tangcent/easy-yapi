package com.itangcent.spi

import com.itangcent.common.spi.ProxyBean
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.spi.ContextProxyBean
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

/**
 * A provider that loads all available implementations of a service type and returns a composite proxy.
 * The composite proxy delegates method calls to all implementations in order.
 */
abstract class SpiCompositeBeanProvider<T : Any> : AbstractSpiBeanProvider<T>() {

    @Suppress("UNCHECKED_CAST")
    override fun loadBean(actionContext: ActionContext, kClass: KClass<T>): T {
        val services = SpiCompositeLoader.load<T>(actionContext, kClass)
        if (services.isEmpty()) {
            throw IllegalStateException("No services found for ${kClass.qualifiedName}")
        }
        
        // If there's only one service, return it directly
        if (services.size == 1) {
            return services[0]
        }
        
        // Create a composite proxy that delegates to all services
        return Proxy.newProxyInstance(
            kClass.java.classLoader,
            arrayOf(kClass.java),
            ProxyBean(arrayOf(*services))
        ) as T
    }
} 