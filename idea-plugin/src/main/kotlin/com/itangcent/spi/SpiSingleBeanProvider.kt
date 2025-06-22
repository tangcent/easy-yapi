package com.itangcent.spi

import com.itangcent.intellij.context.ActionContext
import kotlin.reflect.KClass

/**
 * A provider that loads the first available implementation of a service type.
 */
abstract class SpiSingleBeanProvider<T : Any> : AbstractSpiBeanProvider<T>() {

    override fun loadBean(actionContext: ActionContext, kClass: KClass<T>): T {
        val services = SpiCompositeLoader.load(actionContext, kClass)
        if (services.isEmpty()) {
            throw IllegalStateException("No services found for ${kClass.qualifiedName}")
        }
        return services.first() as T
    }
}