package com.itangcent.easyapi.util

/**
 * String values that represent boolean `true`.
 * Used for lenient boolean parsing from strings.
 */
val TRUE_VALUES = arrayOf("true", "1", "yes", "y", "on")

/**
 * String values that represent boolean `false`.
 * Used for lenient boolean parsing from strings.
 */
val FALSE_VALUES = arrayOf("false", "0", "no", "n", "off")

/**
 * Converts this string to an [Int], or returns `null` if the string is not a valid integer.
 *
 * Handles whitespace by trimming before parsing.
 *
 * @return The integer value, or `null` if parsing fails or this string is `null`.
 */
fun String?.asInt(): Int? {
    return this?.trim()?.toIntOrNull()
}

/**
 * Converts this string to a [Boolean], or returns `null` if the string is not a recognized boolean value.
 *
 * Recognized true values: "true", "1", "yes", "y", "on" (case-insensitive)
 * Recognized false values: "false", "0", "no", "n", "off" (case-insensitive)
 *
 * Handles whitespace by trimming before parsing.
 *
 * @return The boolean value, or `null` if this string is `null` or not a recognized boolean value.
 */
fun String?.asBooleanOrNull(): Boolean? {
    if (this == null) return null
    val s = this.trim().lowercase()
    return when (s) {
        in TRUE_VALUES -> true
        in FALSE_VALUES -> false
        else -> null
    }
}

/**
 * Converts this string to a [Boolean], returning [defaultValue] if the string is not a recognized boolean value.
 *
 * @param defaultValue The value to return if parsing fails. Defaults to `false`.
 * @return The boolean value, or [defaultValue] if parsing fails.
 */
fun String?.asBoolean(defaultValue: Boolean = false): Boolean {
    return this.asBooleanOrNull() ?: defaultValue
}

/**
 * Converts this value to an [Int], or returns `null` if conversion is not possible.
 *
 * Handles the following types:
 * - `null` -> `null`
 * - `Int` -> itself
 * - `Number` -> `toInt()`
 * - `String` -> parses as integer
 * - `Boolean` -> `1` for `true`, `0` for `false`
 * - Other types -> attempts to parse `toString()` as integer
 *
 * @return The integer value, or `null` if conversion fails.
 */
fun Any?.asInt(): Int? {
    return when (this) {
        null -> null
        is Int -> this
        is Number -> this.toInt()
        is String -> this.asInt()
        is Boolean -> if (this) 1 else 0
        else -> this.toString().asInt()
    }
}

/**
 * Converts this value to a [Boolean], or returns `null` if conversion is not possible.
 *
 * Handles the following types:
 * - `null` -> `null`
 * - `Boolean` -> itself
 * - `Number` -> `true` if non-zero, `false` if zero
 * - `String` -> parses using [String.asBooleanOrNull]
 * - Other types -> attempts to parse `toString()` as boolean
 *
 * @return The boolean value, or `null` if conversion fails.
 */
fun Any?.asBooleanOrNull(): Boolean? {
    return when (this) {
        null -> null
        is Boolean -> this
        is Number -> this.toDouble() != 0.0
        is String -> this.asBooleanOrNull()
        else -> this.toString().asBooleanOrNull()
    }
}

/**
 * Converts this value to a [Boolean], returning [defaultValue] if conversion is not possible.
 *
 * @param defaultValue The value to return if conversion fails. Defaults to `false`.
 * @return The boolean value, or [defaultValue] if conversion fails.
 */
fun Any?.asBoolean(defaultValue: Boolean = false): Boolean {
    return this.asBooleanOrNull() ?: defaultValue
}
