package com.itangcent.easyapi.rule.context

/**
 * Wrapper for the field path context exposed to Groovy scripts as `fieldContext`.
 *
 * Provides path-based helpers so scripts can call:
 * - `fieldContext.path()` — returns the current JSON path of the field (e.g. `"user.name"`)
 * - `fieldContext.property(name)` — returns a child path (e.g. `"user.ignoredField"`)
 *
 * @param fieldPath The dot-separated JSON path of the current field
 */
class ScriptFieldContext(private val fieldPath: String) {

    /** Returns the full JSON path of the current field. */
    fun path(): String = fieldPath

    /**
     * Returns the JSON path for a named property relative to the parent of this field.
     * E.g. if fieldPath is "user.name", property("age") returns "user.age".
     * If fieldPath has no parent (top-level), returns the property name directly.
     */
    fun property(name: String): String {
        val lastDot = fieldPath.lastIndexOf('.')
        return if (lastDot >= 0) "${fieldPath.substring(0, lastDot)}.$name" else name
    }

    override fun toString(): String = fieldPath
}
