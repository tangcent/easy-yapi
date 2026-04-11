package com.itangcent.easyapi.extension

data class ExtensionConfig(
    val code: String,
    val description: String,
    val content: String,
    val onClass: String? = null,
    val defaultEnabled: Boolean = false
) {
    companion object {
        val EMPTY = ExtensionConfig(
            code = "",
            description = "",
            content = "",
            onClass = null,
            defaultEnabled = false
        )
    }
}
