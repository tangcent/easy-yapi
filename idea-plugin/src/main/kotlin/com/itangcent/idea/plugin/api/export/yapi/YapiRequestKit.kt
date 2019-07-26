package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.common.model.Request
import java.util.*


fun Request.getStatus(): String {
    val status = this.getExt<String?>("status") ?: return "done"

    return when (status) {
        "undone" -> "undone"
        else -> "done"
    }
}

fun Request.setStatus(status: String?) {
    if (!status.isNullOrBlank()) {
        this.setExt("status", status)
    }
}

fun Request.getTags(): List<String>? {
    return this.getExt("tags") ?: EMPTY_TAGS
}

fun Request.setTags(tags: List<String>?) {
    if (!tags.isNullOrEmpty()) {
        this.setExt("tags", tags)
    }
}

val EMPTY_TAGS: List<String> = Collections.emptyList<String>()