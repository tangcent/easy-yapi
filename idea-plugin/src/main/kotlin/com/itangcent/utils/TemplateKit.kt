package com.itangcent.utils

/**
 * A utility object for working with templates.
 */
object TemplateKit {

    /**
     * Resolves placeholders that may contain characters in a variety of formats.
     *
     * @param placeHolder The placeholder to resolve. This can be null or an object of type Char, String, Array, or Collection.
     * @return An array of characters, or null if the `placeHolder` argument is null or does not contain any characters.
     */
    fun resolvePlaceHolder(placeHolder: Any?): CharArray? {
        if (placeHolder == null) {
            return null
        }
        val placeHolders = LinkedHashSet<Char>()
        resolvePlaceHolder(placeHolder) { placeHolders.add(it) }
        return placeHolders.takeIf { it.isNotEmpty() }?.toCharArray()
    }

    /**
     * A helper function used to recursively resolve placeholders.
     *
     * @param placeHolder The placeholder to resolve. This can be an object of type Char, String, Array, or Collection.
     * @param handle A function used to handle each character found in the placeholder.
     */
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