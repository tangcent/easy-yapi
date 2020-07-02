package com.itangcent.idea.utils

import com.itangcent.common.utils.Extensible

fun Extensible.setExts(exts: Map<String, Any?>) {
    exts.forEach { (t, u) ->
        this.setExt(t, u)
    }
}