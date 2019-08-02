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

fun Any?.toInt(): Int? {
    if (this == null) return null
    if (this is Boolean) {
        return when {
            this -> 1
            else -> 0
        }
    }
    if (this is Number) return this.toInt()
    if (this is String) return this.toIntOrNull()
    return null
}

fun Any?.toBoolean(): Boolean? {
    if (this == null) return null
    if (this is Boolean) return this
    if (this is Number) return this.toInt() == 1
    if (this is String) return this == "true"
    return null
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
