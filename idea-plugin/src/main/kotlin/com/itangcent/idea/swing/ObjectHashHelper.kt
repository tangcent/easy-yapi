package com.itangcent.idea.swing

class ObjectHashHelper {

    private var hashCache: HashMap<Int, Any> = HashMap()

    /**
     * get hash of bean and resolve the hash collisions
     */
    @Synchronized
    fun hash(obj: Any): Int {
        var hashCodeCandidate = rehash(obj)

        while (true) {
            val existed = hashCache[hashCodeCandidate]
            if (existed == null) {
                hashCache[hashCodeCandidate] = obj
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
    internal fun rehash(key: Any?): Int {
        if (key == null) return 0
        val h: Int = key.hashCode()
        return h xor h.ushr(16)
    }


    @Synchronized
    fun getBean(hash: Int): Any? {
        return hashCache[hash]
    }
}