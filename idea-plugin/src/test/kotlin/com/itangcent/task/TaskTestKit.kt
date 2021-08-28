package com.itangcent.task

fun Task.waitRunning() {
    while (!this.isRunning()) {
        Thread.sleep(100)
    }
}