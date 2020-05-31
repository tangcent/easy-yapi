package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.common.constant.Attrs
import com.itangcent.common.utils.Extensible
import com.itangcent.common.utils.notNullOrBlank
import java.util.*


fun Extensible.getStatus(): String {
    val status = this.getExt<String?>("status") ?: return "done"

    return when (status) {
        "undone" -> "undone"
        else -> "done"
    }
}

fun Extensible.setStatus(status: String?) {
    if (!status.isNullOrBlank()) {
        this.setExt("status", status)
    }
}

fun Extensible.getTags(): List<String>? {
    return this.getExt("tags") ?: EMPTY_TAGS
}

fun Extensible.setTags(tags: List<String>?) {
    if (!tags.isNullOrEmpty()) {
        this.setExt("tags", tags)
    }
}

fun Extensible.isOpen(): Boolean {
    return this.getExt("open") ?: false
}

fun Extensible.setOpen(open: Boolean?) {
    if (open != null) {
        this.setExt("open", open)
    }
}

fun Extensible.getDemo(): String? {
    return this.getExt(Attrs.DEMO_ATTR)
}

fun Extensible.setDemo(demo: String?) {
    if (demo.notNullOrBlank()) {
        this.setExt(Attrs.DEMO_ATTR, demo)
    }
}

val EMPTY_TAGS: List<String> = Collections.emptyList<String>()