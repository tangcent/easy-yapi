package com.itangcent.easyapi.dashboard.script

/**
 * A scoped key-value variable store, compatible with Postman's variable scope API.
 *
 * Each scope (environment, globals, collectionVariables, local variables) is represented
 * by an instance of this class. Variables can be get, set, and unset, and dynamic
 * variables (like `{{$timestamp}}`) can be resolved via [replaceIn].
 *
 * Groovy usage:
 * ```groovy
 * pm.environment.set("base_url", "https://api.example.com")
 * def url = pm.environment.get("base_url")
 * pm.environment.unset("temp_var")
 * ```
 *
 * @property variables The backing mutable map for this scope
 */
open class PmVariableScope(
    private val variables: MutableMap<String, String> = mutableMapOf()
) {

    /** Checks whether a variable with the given name exists in this scope. */
    open fun has(name: String): Boolean = variables.containsKey(name)

    /**
     * Retrieves a variable value by name.
     *
     * @param name The variable key
     * @return The value, or null if not found
     */
    open fun get(name: String): String? = variables[name]

    /**
     * Sets a variable. If [value] is null, the variable is removed (equivalent to [unset]).
     * Non-null values are stored as their string representation.
     *
     * @param name The variable key
     * @param value The value to store; null removes the variable
     */
    open fun set(name: String, value: Any?) {
        if (value != null) {
            variables[name] = value.toString()
        } else {
            variables.remove(name)
        }
    }

    /** Removes a variable from this scope. */
    open fun unset(name: String) {
        variables.remove(name)
    }

    /** Removes all variables from this scope. */
    fun clear() {
        variables.clear()
    }

    /** Returns an immutable snapshot of all variables in this scope. */
    open fun toObject(): Map<String, String> = variables.toMap()

    /**
     * Replaces dynamic variable placeholders in the given string.
     *
     * Placeholders use the syntax `{{$variableName}}`. Supported dynamic variables:
     * - `{{$timestamp}}` — current Unix timestamp
     * - `{{$guid}}` / `{{$randomUuid}}` — random UUID
     * - `{{$randomInt}}` — random integer 0–999
     * - `{{$randomAlphaNumeric}}` — 8-char random alphanumeric string
     * - `{{$randomEmail}}`, `{{$randomUrl}}`, `{{$randomIP}}`, etc.
     *
     * @param str The string containing potential placeholders
     * @return The string with all resolvable placeholders replaced
     */
    fun replaceIn(str: String): String {
        var result = str
        val regex = Regex("\\{\\{\\$(\\w+)}}")
        regex.findAll(str).forEach { match ->
            val varName = match.groupValues[1]
            val value = DynamicVariables.resolve(varName)
            if (value != null) {
                result = result.replace(match.value, value)
            }
        }
        return result
    }

    /** Bulk-puts all entries from the given map into this scope. */
    internal fun putAll(map: Map<String, String>) {
        variables.putAll(map)
    }

    /** Returns an immutable snapshot as a plain map. */
    internal fun toMap(): Map<String, String> = variables.toMap()
}

/**
 * A composite variable scope that resolves variables across multiple scopes
 * following Postman's precedence: local → environment → collectionVariables → globals.
 *
 * `get()` and `has()` search all scopes in precedence order (narrowest first).
 * `set()` writes to the local scope only (creating a local variable).
 * `unset()` removes from the local scope only.
 *
 * This is used for `pm.variables` to match Postman's behavior where
 * `pm.variables.get("key")` resolves from the narrowest scope.
 *
 * @param local The local (script-scoped) variables — set() writes here
 * @param environment The active environment scope
 * @param collectionVariables The collection (project) scope
 * @param globals The global scope
 */
class CompositeVariableScope(
    private val local: PmVariableScope,
    private val environment: PmVariableScope,
    private val collectionVariables: PmVariableScope,
    private val globals: PmVariableScope
) : PmVariableScope(local.toMap().toMutableMap()) {

    private val scopes: List<PmVariableScope> = listOf(local, environment, collectionVariables, globals)

    override fun get(name: String): String? {
        return scopes.firstNotNullOfOrNull { it.get(name) }
    }

    override fun has(name: String): Boolean {
        return scopes.any { it.has(name) }
    }

    override fun set(name: String, value: Any?) {
        local.set(name, value)
    }

    override fun unset(name: String) {
        local.unset(name)
    }

    override fun toObject(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (scope in scopes.reversed()) {
            result.putAll(scope.toObject())
        }
        return result
    }
}
