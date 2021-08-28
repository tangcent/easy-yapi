package com.itangcent.utils

import kotlin.test.fail

object WaitHelper {

    fun waitUtil(timeOut: Long, condition: () -> Boolean) {
        val t = System.currentTimeMillis() + timeOut
        while (true) {
            if (condition()) {
                return
            }
            if (System.currentTimeMillis() > t) {
                break
            }
            Thread.sleep(200)
        }
        fail()
    }
}