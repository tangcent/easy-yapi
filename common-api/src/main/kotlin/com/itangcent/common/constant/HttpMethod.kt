package com.itangcent.common.constant

object HttpMethod {
    const val NO_METHOD = "ALL"
    const val GET = "GET"
    const val POST = "POST"
    const val DELETE = "DELETE"
    const val PUT = "PUT"
    const val PATCH = "PATCH"
    const val OPTIONS = "OPTIONS"
    const val TRACE = "TRACE"
    const val HEAD = "HEAD"

    val ALL_METHODS = arrayOf(GET, POST, DELETE, PUT,
        PATCH, OPTIONS, TRACE, HEAD)

    /**
     * fix method to match the standard
     */
    fun preferMethod(method: String): String {
        if (method.isBlank()) {
            return NO_METHOD
        }
        val standardMethod = method.toUpperCase()
        if (ALL_METHODS.contains(standardMethod)) {
            return standardMethod
        }
        for (me in ALL_METHODS) {
            if (standardMethod.contains(".$me")) {
                return me
            }
        }
        for (me in ALL_METHODS) {
            if (standardMethod.contains(me)) {
                return me
            }
        }
        return NO_METHOD
    }
}