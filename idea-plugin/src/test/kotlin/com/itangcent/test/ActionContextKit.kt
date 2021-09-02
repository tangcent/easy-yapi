package com.itangcent.test

import org.mockito.Mockito
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
fun com.itangcent.intellij.context.ActionContext.ActionContextBuilder.mock(type: KClass<*>) {
    this.bindInstance(type as KClass<Any>, Mockito.mock(type.java) as Any)
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> com.itangcent.intellij.context.ActionContext.ActionContextBuilder.mock(
    type: KClass<T>,
    mock: (T) -> Unit
) {
    this.bindInstance(type as KClass<Any>, Mockito.mock(type.java).also { mock(it as T) } as Any)
}