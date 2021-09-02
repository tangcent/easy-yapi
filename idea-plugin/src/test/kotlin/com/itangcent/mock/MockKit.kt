package com.itangcent.mock

inline fun <reified T : Any> any(t: T): T {
    org.mockito.kotlin.any<T>()
    return t
}