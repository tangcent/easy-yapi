package com.itangcent.intellij.extend

import com.itangcent.intellij.context.ActionContext

fun ActionContext?.tryRunAsync(action: () -> Unit) {
    if (this == null) {
        action()
    } else {
        this.runAsync(action)
    }
}