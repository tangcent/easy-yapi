package com.itangcent.easyapi.dashboard.env

/**
 * Represents a named set of key-value variables scoped to either a single project or globally across all projects.
 *
 * Environments allow users to switch between different configuration sets (e.g., dev, staging, production)
 * when making API calls from the dashboard. Variables defined in an environment can be referenced
 * in pre-request and post-response scripts via `pm.environment.get("varName")`.
 *
 * Compatible with Postman's environment model — each environment has a name, a scope, and a map of variables.
 *
 * @property name Unique identifier for this environment
 * @property scope Whether this environment is visible globally or only within the current project
 * @property variables Key-value pairs that can be resolved at request time
 */
data class Environment(
    val name: String,
    val scope: EnvironmentScope = EnvironmentScope.PROJECT,
    val variables: Map<String, String> = emptyMap()
)

/**
 * Determines the visibility and persistence location of an [Environment].
 *
 * - [GLOBAL]: Stored in application-level settings; visible across all IntelliJ projects
 * - [PROJECT]: Stored in project-level settings; visible only within the owning project
 */
enum class EnvironmentScope {
    GLOBAL,
    PROJECT;

    /** Returns a human-readable label for display in UI components. */
    fun label(): String = when (this) {
        GLOBAL -> "Global"
        PROJECT -> "Project"
    }
}

/**
 * Aggregates a list of [Environment] instances with an optional active selection.
 *
 * This is the top-level data structure persisted to settings and loaded by [EnvironmentService].
 * It supports resolving variables from the currently active environment.
 *
 * @property environments All available environments (merged from global + project scope)
 * @property activeEnvironmentName The name of the currently selected environment, or null if none is active
 */
data class EnvironmentData(
    val environments: List<Environment> = emptyList(),
    val activeEnvironmentName: String? = null
) {
    /** Returns the [Environment] whose name matches [activeEnvironmentName], or null. */
    fun activeEnvironment(): Environment? {
        return environments.find { it.name == activeEnvironmentName }
    }

    /**
     * Resolves a single variable from the active environment.
     *
     * @param name The variable key to look up
     * @return The variable value, or null if no active environment or key not found
     */
    fun resolveVariable(name: String): String? {
        val active = activeEnvironment() ?: return null
        return active.variables[name]
    }

    /**
     * Returns all variables from the active environment as an immutable map.
     *
     * @return A map of all variables, or an empty map if no environment is active
     */
    fun resolveAllVariables(): Map<String, String> {
        val active = activeEnvironment() ?: return emptyMap()
        return active.variables
    }
}
