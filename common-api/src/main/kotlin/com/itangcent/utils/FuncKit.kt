package com.itangcent.utils


fun <T> ((T) -> Boolean).and(next: (T) -> Boolean): (T) -> Boolean {
    return { this(it) && next(it) }
}

fun <T> ((T) -> Unit).then(next: (T) -> Unit): (T) -> Unit {
    return {
        this(it)
        next(it)
    }
}

fun <T, T1, T2> ((T, T1, T2) -> Unit).then(next: (T, T1, T2) -> Unit): (T, T1, T2) -> Unit {
    return { t, t1, t2 ->
        this(t, t1, t2)
        next(t, t1, t2)
    }
}