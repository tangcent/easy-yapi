package com.itangcent.idea.swing

import java.lang.ref.WeakReference

class SafeHashHelper {

    private var hashCache: HashMap<Int, WeakReference<Any>> = HashMap()

    /**
     * get hash of bean and resolve the hash collisions
     */
    @Synchronized
    fun hash(obj: Any): Int {
        var hashCodeCandidate = rehash(obj)

        while (true) {
            val existed = hashCache[hashCodeCandidate]?.get()
            if (existed == null) {
                hashCache[hashCodeCandidate] = WeakReference(obj)
                return hashCodeCandidate
            } else {
                if (existed == obj) {
                    return hashCodeCandidate
                } else {
                    ++hashCodeCandidate
                }
            }
        }
    }

    /**
     * rehash,copy from HashMap
     */
    private fun rehash(key: Any?): Int {
        if (key == null) return 0
        val h: Int = key.hashCode()
        return h xor h.ushr(16)
    }

    @Synchronized
    fun getBean(hash: Int): Any? {
        return hashCache[hash]?.get()
    }
}