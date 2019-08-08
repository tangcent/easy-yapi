package com.itangcent.idea.plugin.utils

import java.util.function.Consumer
import java.util.function.Function

object KtHelper {

    @Suppress("UNCHECKED_CAST")
    public open fun <T : kotlin.Any> ktFunction(callBack: Any): (T) -> kotlin.Unit {

        try {
            (callBack as (T) -> kotlin.Unit)
            return callBack
        } catch (e: Exception) {
            if (callBack is kotlin.jvm.functions.Function1<*, *>) {
                return {
                    (callBack as Function1<Any?, Any?>).invoke(it)
                }
            }
            if (callBack is kotlin.Function1<*, *>) {
                return {
                    (callBack as Function1<Any?, Any?>).invoke(it)
                }
            }
            if (callBack is Function<*, *>) {
                return {
                    (callBack as Function<Any?, Any?>).apply(it as Any?)
                }
            }
            if (callBack is Consumer<*>) {
                return {
                    (callBack as Consumer<Any?>).accept(it as Any?)
                }
            }
            if (callBack is groovy.lang.Closure<*>) {
                return {
                    (callBack as groovy.lang.Closure<Any?>).call(it as Any?)
                }
            }
        }

        throw ClassCastException("$callBack cannot be cast to kotlin.jvm.functions.Function1")
    }

}