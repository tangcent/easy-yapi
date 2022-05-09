package com.itangcent.utils

import java.util.concurrent.atomic.AtomicBoolean

fun <T> Function0<T?>.disposable(): Function0<T?> {
    if (this is DisposableFunction0) {
        return this
    }
    return DisposableFunction0(this)
}

@Suppress("UNCHECKED_CAST")
fun Function0<*>.asUnit(): Function0<Unit> {
    return this as Function0<Unit>
}

fun <P, R> Function1<P, R?>.disposable(): Function1<P, R?> {
    if (this is DisposableFunction1) {
        return this
    }
    return DisposableFunction1(this)
}

class DisposableFunction0<T>(val function: Function0<T?>) : Function0<T?> {
    private var todo = AtomicBoolean(true)

    override fun invoke(): T? {
        if (todo.compareAndSet(true, false)) {
            return function()
        }
        return null
    }
}

class DisposableFunction1<P, R>(val function: Function1<P, R?>) : Function1<P, R?> {
    private var todo = AtomicBoolean(true)

    override fun invoke(p1: P): R? {
        if (todo.compareAndSet(true, false)) {
            return function(p1)
        }
        return null
    }
}