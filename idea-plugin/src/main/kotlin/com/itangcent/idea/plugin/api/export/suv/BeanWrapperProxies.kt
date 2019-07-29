package com.itangcent.idea.plugin.api.export.suv

import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass
import kotlin.reflect.full.cast


object BeanWrapperProxies {

    /**
     * clazz should be interface
     */
    fun <T : Any> wrap(clazz: KClass<T>, delegate: T): T {
        val loader = clazz.java.classLoader
        val interfaces = arrayOf(clazz.java)
        return clazz.cast(Proxy.newProxyInstance(loader, interfaces, InvocationHandler(delegate)))
    }

    class InvocationHandler<T : Any>(private var delegate: T) : java.lang.reflect.InvocationHandler {

        override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
            return when (args) {
                null -> method!!.invoke(delegate)
                else -> method!!.invoke(delegate, *args)
            }
        }

    }
}