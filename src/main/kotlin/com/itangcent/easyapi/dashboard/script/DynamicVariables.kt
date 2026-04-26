package com.itangcent.easyapi.dashboard.script

/**
 * Registry of dynamic variable resolvers, compatible with Postman's dynamic variables.
 *
 * Dynamic variables are referenced in strings using the `{{$variableName}}` syntax
 * and are resolved at request time by [PmVariableScope.replaceIn].
 *
 * Built-in dynamic variables:
 * - `{{$timestamp}}` — Current Unix timestamp (seconds since epoch)
 * - `{{$randomInt}}` — Random integer between 0 and 999
 * - `{{$guid}}` / `{{$randomUuid}}` — Random UUID
 * - `{{$randomAlphaNumeric}}` — 8-character random alphanumeric string
 * - `{{$randomFirstName}}` — Random first name from a preset list
 * - `{{$randomLastName}}` — Random last name from a preset list
 * - `{{$randomEmail}}` — Random email address
 * - `{{$randomUrl}}` — Random URL
 * - `{{$randomIP}}` — Random IPv4 address
 *
 * Custom resolvers can be registered at runtime via [register].
 *
 * Example:
 * ```kotlin
 * DynamicVariables.register("myCustomVar") { "custom-value" }
 * val value = DynamicVariables.resolve("myCustomVar") // "custom-value"
 * ```
 */
object DynamicVariables {

    private val resolvers = mutableMapOf<String, () -> String>(
        "timestamp" to { (System.currentTimeMillis() / 1000).toString() },
        "randomInt" to { (Math.random() * 1000).toInt().toString() },
        "guid" to { java.util.UUID.randomUUID().toString() },
        "randomAlphaNumeric" to {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            (1..8).map { chars[(Math.random() * chars.length).toInt()] }.joinToString("")
        },
        "randomFirstName" to {
            val names = listOf("Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry")
            names[(Math.random() * names.size).toInt()]
        },
        "randomLastName" to {
            val names = listOf("Smith", "Johnson", "Williams", "Brown", "Jones", "Davis", "Miller", "Wilson")
            names[(Math.random() * names.size).toInt()]
        },
        "randomEmail" to {
            val user = (1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789"[(Math.random() * 36).toInt()] }.joinToString("")
            val domains = listOf("example.com", "test.org", "mail.net")
            "$user@${domains[(Math.random() * domains.size).toInt()]}"
        },
        "randomUrl" to {
            val paths = listOf("api", "v1", "users", "items")
            "https://example.com/${paths[(Math.random() * paths.size).toInt()]}"
        },
        "randomIP" to {
            "${(Math.random() * 255).toInt()}.${(Math.random() * 255).toInt()}.${(Math.random() * 255).toInt()}.${(Math.random() * 255).toInt()}"
        },
        "randomUuid" to { java.util.UUID.randomUUID().toString() }
    )

    /**
     * Resolves a dynamic variable by name.
     *
     * @param name The variable name (without the `{{$` and `}}` delimiters)
     * @return The resolved value, or null if no resolver is registered for the name
     */
    fun resolve(name: String): String? = resolvers[name]?.invoke()

    /**
     * Registers a custom dynamic variable resolver.
     *
     * @param name The variable name
     * @param resolver A function that returns the resolved value each time it is called
     */
    fun register(name: String, resolver: () -> String) {
        resolvers[name] = resolver
    }
}
