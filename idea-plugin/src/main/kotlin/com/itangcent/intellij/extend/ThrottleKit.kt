package com.itangcent.intellij.extend

import com.itangcent.intellij.extend.rx.Throttle

fun Throttle.acquireGreedy(cd: Long) {
    while (true) {
        if (this.acquire(cd)) {
            return
        }
        Thread.sleep(cd / 4)
    }
}
