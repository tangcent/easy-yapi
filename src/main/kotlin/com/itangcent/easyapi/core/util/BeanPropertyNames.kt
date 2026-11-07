package com.itangcent.easyapi.core.util

/**
 * Derives JavaBeans property names from accessor method names.
 *
 * Decapitalization follows `java.beans.Introspector.decapitalize`: a name whose
 * first two characters are both upper-case is left untouched. Lowering only the
 * first character would turn `getURLValue()` into `uRLValue`, which no longer
 * matches the `URLValue` field and makes the model export both as separate
 * properties (the issue #1492 class of bug).
 *
 * This is also the standard bean naming Jackson 3 defaults to; Jackson 2 needs
 * `MapperFeature.USE_STD_BEAN_NAMING` to opt in.
 */
object BeanPropertyNames {

    fun decapitalize(name: String): String {
        if (name.length > 1 && name[0].isUpperCase() && name[1].isUpperCase()) {
            return name
        }
        return name.replaceFirstChar { it.lowercaseChar() }
    }

    /**
     * Strips a `get` / `is` / `set` prefix and decapitalizes the remainder.
     * A non-accessor method name is returned unchanged.
     */
    fun fromAccessor(methodName: String): String = when {
        methodName.startsWith("get") && methodName.length > 3 ->
            decapitalize(methodName.substring(3))

        methodName.startsWith("is") && methodName.length > 2 ->
            decapitalize(methodName.substring(2))

        methodName.startsWith("set") && methodName.length > 3 ->
            decapitalize(methodName.substring(3))

        else -> methodName
    }
}
