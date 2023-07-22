package com.itangcent.test

import org.mockito.Mockito
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
fun com.itangcent.intellij.context.ActionContext.ActionContextBuilder.mock(type: KClass<*>) {
    this.bindInstance(type as KClass<Any>, Mockito.mock(type.java) as Any)
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> com.itangcent.intellij.context.ActionContext.ActionContextBuilder.mock(
    type: KClass<T>,
    mock: KStubbing<T>.(T) -> Unit
) {
    val mockCls = type.java
    this.bindInstance(type as KClass<Any>, Mockito.mock(mockCls).also {
        KStubbing(it).mock(it)
    } as Any)
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any> com.itangcent.intellij.context.ActionContext.ActionContextBuilder.mock(
    stubbing: KStubbing<T>.(T) -> Unit
) {
    this.bindInstance(
        T::class as KClass<Any>,
        mock<T>().stub(stubbing) as Any
    )
}