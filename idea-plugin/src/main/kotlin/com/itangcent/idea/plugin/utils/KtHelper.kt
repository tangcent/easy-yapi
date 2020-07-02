package com.itangcent.idea.plugin.utils

import java.util.function.Consumer
import java.util.function.Function

object KtHelper {

    @Deprecated(message = "use [asConsumer]", replaceWith = ReplaceWith(
            "asConsumer(callBack)",
            "com.itangcent.idea.plugin.utils.KtHelper.asConsumer"))
    @Suppress("UNCHECKED_CAST", "UNUSED")
    fun <T : kotlin.Any> ktFunction(callBack: Any): (T) -> kotlin.Unit {
        return asConsumer(callBack)
    }

    @Suppress("UNCHECKED_CAST", "UNUSED")
    fun <T : kotlin.Any> asConsumer(callBack: Any): (T) -> kotlin.Unit {
        try {
            (callBack as (T) -> kotlin.Unit)
            return callBack
        } catch (e: Exception) {
        }
        when (callBack) {
            is Function1<*, *> -> {
                return {
                    (callBack as Function1<Any?, Any?>).invoke(it)
                }
            }
            is Function<*, *> -> {
                return {
                    (callBack as Function<Any?, Any?>).apply(it as Any?)
                }
            }
            is Consumer<*> -> {
                return {
                    (callBack as Consumer<Any?>).accept(it as Any?)
                }
            }
            is groovy.lang.Closure<*> -> {
                return {
                    (callBack as groovy.lang.Closure<Any?>).call(it as Any?)
                }
            }
            else -> throw ClassCastException("$callBack cannot be cast to kotlin.jvm.functions.Function1")
        }
    }

    @Suppress("UNCHECKED_CAST", "UNUSED")
    fun <T : kotlin.Any, R : kotlin.Any> asFunction(callBack: Any): (T) -> (R?) {
        try {
            return (callBack as (T) -> R)
        } catch (e: Exception) {
        }
        when (callBack) {
            is Function1<*, *> -> {
                return {
                    (callBack as Function1<Any?, Any?>).invoke(it) as R?
                }
            }
            is Function<*, *> -> {
                return {
                    (callBack as Function<Any?, Any?>).apply(it as Any?) as R?
                }
            }
            is Consumer<*> -> {
                return {
                    (callBack as Consumer<Any?>).accept(it as Any?)
                    null
                }
            }
            is groovy.lang.Closure<*> -> {
                return {
                    (callBack as groovy.lang.Closure<Any?>).call(it as Any?) as R?
                }
            }
            else -> throw ClassCastException("$callBack cannot be cast to kotlin.jvm.functions.Function1")
        }
    }

}