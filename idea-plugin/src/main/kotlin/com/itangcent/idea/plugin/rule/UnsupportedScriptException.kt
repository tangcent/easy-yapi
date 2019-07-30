package com.itangcent.idea.plugin.rule

class UnsupportedScriptException : RuntimeException {

    private val type: String

    constructor(type: String) : super("$type was unsupported") {
        this.type = type
    }

    fun getType(): String {
        return type
    }
}