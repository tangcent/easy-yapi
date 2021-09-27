package com.itangcent.utils

import com.itangcent.common.utils.mapToTypedArray

object TemplateKit {

    fun resolvePlaceHolder(placeHolder: Any?): Array<Char>? {
        if (placeHolder == null) {
            return null
        }
        val placeHolders = ArrayList<Char>()
        resolvePlaceHolder(placeHolder) { placeHolders.add(it) }
        return placeHolders.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    private fun resolvePlaceHolder(placeHolder: Any?, handle: (Char) -> Unit) {
        when (placeHolder) {
            is Char -> {
                handle(placeHolder)
            }
            is String -> {
                placeHolder.toCharArray().forEach(handle)
            }
            is Array<*> -> {
                placeHolder.forEach { resolvePlaceHolder(it, handle) }
            }
            is Collection<*> -> {
                placeHolder.forEach { resolvePlaceHolder(it, handle) }
            }
        }
    }
}