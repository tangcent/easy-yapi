package com.itangcent.mock

import com.itangcent.common.utils.getPropertyValue
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject

inline fun <reified T : Any> any(t: T): T {
    org.mockito.kotlin.any<T>()
    return t
}

private fun Class<*>.findAccessibleField(fieldName: String): Field {
    for (field in this.fields) {
        if (field.name == fieldName) {
            field.markAsAccessible()
            return field
        }
    }
    for (field in this.declaredFields) {
        if (field.name == fieldName) {
            field.markAsAccessible()
            return field
        }
    }
    throw NoSuchFieldException("Class: $this fieldName: $fieldName")
}

private fun Field.markAsAccessible() {
    this.isAccessible = true
    if (this.name == "modifiers") {
        return
    }
    val modifiers = this.modifiers and Modifier.FINAL.inv()
    if (modifiers != this.modifiers) {
        modifierField.setInt(this, modifiers)
    }
}

private val modifierField by lazy {
    Field::class.java.findAccessibleField("modifiers")
}

private val lazyValValueField by lazy {
    kotlin.reflect.jvm.internal.ReflectProperties.LazyVal::class.java.findAccessibleField("value")
}

private fun companionField(clazz: KClass<*>): Field {
    return clazz.java.findAccessibleField("Companion")
}

fun withMockCompanion(clazz: KClass<*>, mockInstance: Any, block: () -> Unit) {
    val companionClass: KClass<*> = clazz.companionObject!!
    val companionField = companionField(clazz)
    val data: Any =
        (companionClass.getPropertyValue("data") as kotlin.reflect.jvm.internal.ReflectProperties.LazyVal<*>)()
    val objectInstanceDelegate =
        data.getPropertyValue("objectInstance\$delegate") as kotlin.reflect.jvm.internal.ReflectProperties.LazyVal<*>
    val originalInstance = companionClass.objectInstance
    try {
        companionField.set(clazz, mockInstance)
        lazyValValueField.set(objectInstanceDelegate, mockInstance)
        block()
    } finally {
        companionField.set(clazz, originalInstance)
        lazyValValueField.set(objectInstanceDelegate, originalInstance)
    }
}