package com.itangcent.idea.plugin.json

interface JsonFormatter {

    fun format(obj: Any?, desc: String? = null): String
}