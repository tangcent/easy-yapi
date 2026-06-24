package com.itangcent.easyapi.dashboard.sync

import com.itangcent.easyapi.exporter.postman.PostmanApiClient
import com.itangcent.easyapi.exporter.postman.UploadResult
import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentDetail
import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentValue
import com.itangcent.easyapi.script.env.Environment
import com.itangcent.easyapi.script.env.EnvironmentScope

/**
 * Pure logic for environment synchronization, extracted for testability.
 *
 * Contains conflict resolution and error message conversion — logic that is
 * specific to the sync service layer. Pure data mapping functions live in
 * [PostmanApiClient.Companion] per spec tasks 2.5 and 2.6.
 *
 * @see EnvironmentSyncService for the service that uses this logic
 */
internal object EnvironmentSyncLogic {

    // ── Mapping: EasyAPI → Postman ───────────────────────────────────────────

    /**
     * Maps an EasyAPI [Environment] to a [PostmanEnvironmentDetail].
     *
     * Delegates the variable mapping to [PostmanApiClient.environmentToVariables]
     * (spec task 2.5).
     *
     * @see EnvironmentSyncService.pushToPostman
     */
    fun environmentToPostman(env: Environment): PostmanEnvironmentDetail {
        return PostmanEnvironmentDetail(
            name = env.name,
            values = PostmanApiClient.environmentToVariables(env)
        )
    }

    /**
     * Maps EasyAPI variables to a list of [PostmanEnvironmentValue] entries.
     *
     * Delegates to [PostmanApiClient.variablesToPostmanValues] (spec task 2.5).
     */
    fun variablesToPostmanValues(variables: Map<String, String>): List<PostmanEnvironmentValue> {
        return PostmanApiClient.variablesToPostmanValues(variables)
    }

    // ── Mapping: Postman → EasyAPI ───────────────────────────────────────────

    /**
     * Maps Postman environment values to a simple key-value map.
     *
     * Delegates to [PostmanApiClient.postmanVariablesToMap] (spec task 2.6).
     *
     * @param values Postman variable list
     * @param includeDisabled Whether to include disabled variables
     * @return Map of variable key → value
     */
    fun postmanVariablesToMap(
        values: List<PostmanEnvironmentValue>,
        includeDisabled: Boolean
    ): Map<String, String> {
        return PostmanApiClient.postmanVariablesToMap(values, includeDisabled)
    }

    // ── Conflict Resolution ──────────────────────────────────────────────────

    /**
     * Resolves a conflict between an existing local environment and a Postman environment.
     *
     * @param localExisting The existing local environment (may be null for no conflict)
     * @param postmanVariables The variables from Postman
     * @param postmanEnvName The name of the Postman environment
     * @param strategy The conflict resolution strategy
     * @return The resolved [Environment], or null if [ConflictStrategy.SKIP]
     */
    fun resolveConflict(
        localExisting: Environment?,
        postmanVariables: Map<String, String>,
        postmanEnvName: String,
        strategy: ConflictStrategy
    ): Environment? {
        if (localExisting == null) {
            // No conflict — create new PROJECT-scoped environment
            return Environment(
                name = postmanEnvName,
                scope = EnvironmentScope.PROJECT,
                variables = postmanVariables
            )
        }

        return when (strategy) {
            ConflictStrategy.REPLACE -> {
                localExisting.copy(variables = postmanVariables)
            }

            ConflictStrategy.MERGE -> {
                val merged = localExisting.variables.toMutableMap()
                merged.putAll(postmanVariables) // Postman values take precedence
                localExisting.copy(variables = merged)
            }

            ConflictStrategy.SKIP -> null
        }
    }

    // ── Error Handling ───────────────────────────────────────────────────────

    /**
     * Converts API exceptions to user-friendly error messages.
     *
     * Handles network exceptions and generic errors. HTTP status-based errors
     * (401, 429) embedded in exception messages are also translated via
     * [friendlyErrorMessage], which is shared with [uploadResultErrorMessage]
     * so that status-code handling lives in exactly one place.
     */
    fun handleApiError(e: Exception): String {
        return friendlyErrorMessage(e.message)
    }

    /**
     * Extracts a user-friendly error message from an [UploadResult].
     *
     * Delegates status-code translation to [friendlyErrorMessage], which is
     * shared with [handleApiError].
     *
     * @param result The upload result from a Postman API call
     * @return User-friendly error message
     */
    fun uploadResultErrorMessage(result: UploadResult): String {
        return friendlyErrorMessage(result.message)
    }

    /**
     * Translates a raw error message into a user-friendly one.
     *
     * Shared by [handleApiError] (for exceptions) and [uploadResultErrorMessage]
     * (for `UploadResult` messages), so HTTP status-code handling is defined
     * in a single location rather than duplicated.
     */
    private fun friendlyErrorMessage(message: String?): String {
        val msg = message ?: "Unknown error"
        return when {
            msg.contains("401") -> "API token is invalid or expired. Please reconfigure in settings."
            msg.contains("429") -> "Postman API rate limit exceeded. Please retry later."
            else -> msg
        }
    }

    // ── Validation ───────────────────────────────────────────────────────────

    /**
     * Checks whether a Postman API token is configured (non-blank).
     */
    fun isTokenConfigured(token: String?): Boolean {
        return !token.isNullOrBlank()
    }
}
