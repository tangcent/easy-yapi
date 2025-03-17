package com.itangcent.mock

import com.itangcent.intellij.jvm.adapt.getterPropertyName
import com.itangcent.intellij.jvm.adapt.maybeGetterMethodPropertyName
import com.itangcent.intellij.jvm.adapt.maybeSetterMethodPropertyName
import com.itangcent.intellij.jvm.adapt.setterPropertyName
import org.mockito.stubbing.Answer

inline fun <reified T : Any> any(t: T): T {
    org.mockito.kotlin.any<T>()
    return t
}

fun mockFields(): Answer<*> {
    val data = hashMapOf<String, Any?>()
    return Answer<Any?> {
        val name = it.method.name
        if (name.maybeGetterMethodPropertyName()) {
            return@Answer data[name.getterPropertyName()]
        } else if (name.maybeSetterMethodPropertyName()) {
            data[name.setterPropertyName()] = it.arguments[0]
            return@Answer null
        } else {
            return@Answer null
        }
    }
}