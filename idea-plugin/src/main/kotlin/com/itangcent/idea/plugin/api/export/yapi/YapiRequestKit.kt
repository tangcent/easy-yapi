package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.common.model.Doc
import java.util.*


fun Doc.getStatus(): String {
    val status = this.getExt<String?>("status") ?: return "done"

    return when (status) {
        "undone" -> "undone"
        else -> "done"
    }
}

fun Doc.setStatus(status: String?) {
    if (!status.isNullOrBlank()) {
        this.setExt("status", status)
    }
}

fun Doc.getTags(): List<String>? {
    return this.getExt("tags") ?: EMPTY_TAGS
}

fun Doc.setTags(tags: List<String>?) {
    if (!tags.isNullOrEmpty()) {
        this.setExt("tags", tags)
    }
}

val EMPTY_TAGS: List<String> = Collections.emptyList<String>()