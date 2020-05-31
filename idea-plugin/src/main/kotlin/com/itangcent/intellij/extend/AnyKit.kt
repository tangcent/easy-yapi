package com.itangcent.intellij.extend

fun <K, V> Map<K, V>.asHashMap(): HashMap<K, V> {
    if (this is HashMap<K, V>) {
        return this
    }
    val map: HashMap<K, V> = HashMap()
    this.entries.forEach { map[it.key] = it.value }
    return map
}

fun <E> List<E>.asArrayList(): ArrayList<E> {
    if (this is ArrayList<E>) {
        return this
    }
    val list: ArrayList<E> = ArrayList()
    this.forEach { list.add(it) }
    return list
}

fun Any?.toPrettyString(): String? {
    if (this == null) return null
    if (this is String) return this
    if (this is Array<*>) return "[" + this.joinToString(separator = ", ") { it.toPrettyString() ?: "null" } + "]"
    if (this is Collection<*>) return "[" + this.joinToString(separator = ", ") { it.toPrettyString() ?: "null" } + "]"
    if (this is Map<*, *>) return "{" + this.entries.joinToString(separator = ", ") {
        (it.key.toPrettyString() ?: "null") + ": " + (it.value.toPrettyString() ?: "null")
    } + "}"
    return this.toString()
}

@Suppress("UNCHECKED_CAST")
fun Any.asHashMap(obj: Any?): HashMap<String, Any?> {
    if (obj is HashMap<*, *>) {
        return obj as HashMap<String, Any?>
    }

    if (obj is Map<*, *>) {
        val map: HashMap<String, Any?> = HashMap()
        obj.forEach { (k, v) -> map[k.toString()] = v }
        return map
    }
    return HashMap()
}
