package com.itangcent.utils

object NonReentrant {

    private val flagThreadLocal = ThreadLocal.withInitial { mutableSetOf<String>() }

    fun <T> call(flag: String, action: () -> T): T {
        val flags = flagThreadLocal.get()
        if (flags.contains(flag)) {
            throw NonReentrantException(flag)
        }
        flags.add(flag)
        try {
            return action()
        } finally {
            flags.remove(flag)
        }
    }
}

class NonReentrantException(val flag: String) : Exception("should not reentrant calls [$flag]")