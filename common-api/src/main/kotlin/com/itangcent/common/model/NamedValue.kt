package com.itangcent.common.model

/**
 * Interface for objects that have a name and value.
 */
interface NamedValue<T> {
    /**
     * The name of this value.
     */
    var name: String?

    /**
     * The value.
     */
    var value: T?
}


fun <V, T : NamedValue<V>> List<T>.find(name: String): T? {
    return this.firstOrNull { it.name == name }
}

fun <V, T : NamedValue<V>> List<T>.findIgnoreCase(name: String): T? {
    return this.firstOrNull { it.name?.equals(name, ignoreCase = true) == true }
}