package com.itangcent.test

import org.mockito.Mockito
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
fun com.itangcent.intellij.context.ActionContext.ActionContextBuilder.mock(type: KClass<*>) {
    this.bindInstance(type as KClass<Any>, Mockito.mock(type.java) as Any)
}