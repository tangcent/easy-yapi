package com.itangcent.easyapi.channel.postman.sync

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.channel.postman.CachedPostmanApiClient
import com.itangcent.easyapi.channel.postman.PostmanApiClient
import com.itangcent.easyapi.channel.postman.Workspace
import com.itangcent.easyapi.channel.postman.asCached
import com.itangcent.easyapi.channel.postman.model.PostmanEnvironmentInfo
import com.itangcent.easyapi.channel.postman.PostmanSettings
import com.itangcent.easyapi.channel.spi.EnvironmentSyncSupport
import com.itangcent.easyapi.core.http.HttpClientProvider
import com.itangcent.easyapi.core.ide.support.NotificationUtils
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.script.env.EnvironmentService
import com.itangcent.easyapi.core.settings.settings
import kotlinx.coroutines.runBlocking
import javax.swing.SwingUtilities

/**
 * Project-level service that orchestrates bidirectional environment synchronization
 * between EasyAPI and Postman.
 *
 * Supports:
 * - **Push**: Upload local environments to Postman (create or update by name)
 * - **Pull**: Import Postman environments into local (with conflict resolution)
 *
 * Uses [PostmanApiClient] for API calls and [EnvironmentService] for local persistence.
 *
 * **Decision CO7**: Implements [EnvironmentSyncSupport] (the SPI in
 * `channel.spi`) so `core.*` callers can depend on the abstraction rather than
 * this concrete class. Registered in `plugin.xml` as a project service with
 * `serviceInterface="...EnvironmentSyncSupport"`. Callers MUST look it up via
 * `EnvironmentSyncSupport.getInstance(project)` — never via this class directly
 * (would re-introduce the DAG violation).
 */
class EnvironmentSyncService(private val project: Project) : EnvironmentSyncSupport {

    private val environmentService: EnvironmentService = EnvironmentService.getInstance(project)

    companion object : IdeaLog {
        /**
         * Same-package lookup returning the concrete type (for
         * [EnvironmentSyncDialog] which calls non-SPI methods like
         * [listWorkspaces] and [listPostmanEnvironments]).
         *
         * External callers MUST use
         * `EnvironmentSyncSupport.getInstance(project)` instead —
         * importing this concrete class from `core.*` re-introduces
         * the CO3 DAG violation.
         */
        fun getInstance(project: Project): EnvironmentSyncService =
            EnvironmentSyncSupport.getInstance(project) as EnvironmentSyncService
    }

    /**
     * Push selected local environments to Postman.
     *
     * For each environment name in [envNames]:
     * - If a Postman environment with the same name exists in the workspace → update it
     * - Otherwise → create a new one
     *
     * @param envNames List of local environment names to push
     * @param workspaceId Target Postman workspace ID
     * @return [SyncResult] with succeeded and failed environments
     */
    suspend fun pushToPostman(envNames: List<String>, workspaceId: String): SyncResult {
        LOG.info("pushToPostman: start, ${envNames.size} env(s), workspace=$workspaceId")
        val token = getPostmanToken()
        if (!EnvironmentSyncLogic.isTokenConfigured(token)) {
            LOG.warn("pushToPostman: aborted, Postman API token not configured")
            return SyncResult(
                succeeded = emptyList(),
                failed = envNames.map { SyncFailure(it, "Postman API token is not configured") }
            )
        }

        val client = createCachedClient(token!!, workspaceId)
        val localEnvs = environmentService.getEnvironments().filter { it.name in envNames }

        if (localEnvs.isEmpty()) {
            LOG.warn("pushToPostman: aborted, none of the requested environments found locally")
            return SyncResult(
                succeeded = emptyList(),
                failed = envNames.map { SyncFailure(it, "Environment not found locally") }
            )
        }

        // Fetch existing Postman environments for name matching
        val postmanEnvs = try {
            client.listEnvironments(workspaceId, useCache = false)
        } catch (e: Exception) {
            LOG.warn("pushToPostman: failed to list Postman environments", e)
            return SyncResult(
                succeeded = emptyList(),
                failed = localEnvs.map { SyncFailure(it.name, "Failed to fetch Postman environments: ${e.message}") }
            )
        }

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<SyncFailure>()

        for (env in localEnvs) {
            try {
                val postmanDetail = EnvironmentSyncLogic.environmentToPostman(env)
                val existing = postmanEnvs.find { it.name == env.name }

                val result = if (existing != null) {
                    LOG.info("pushToPostman: updating environment '${env.name}' (id=${existing.id})")
                    client.updateEnvironment(existing.id, workspaceId, postmanDetail)
                } else {
                    LOG.info("pushToPostman: creating environment '${env.name}'")
                    client.createEnvironment(workspaceId, postmanDetail)
                }

                if (result.success) {
                    succeeded.add(env.name)
                } else {
                    failed.add(SyncFailure(env.name, EnvironmentSyncLogic.uploadResultErrorMessage(result)))
                }
            } catch (e: Exception) {
                LOG.warn("pushToPostman: failed for environment '${env.name}'", e)
                failed.add(SyncFailure(env.name, EnvironmentSyncLogic.handleApiError(e)))
            }
        }

        LOG.info("pushToPostman: done, ${succeeded.size} succeeded, ${failed.size} failed")
        return SyncResult(succeeded = succeeded, failed = failed)
    }

    /**
     * Pull selected Postman environments into local.
     *
     * For each Postman environment ID in [postmanEnvIds]:
     * - Fetch full detail from Postman API
     * - If a local environment with the same name exists → apply [conflictStrategy]
     * - Otherwise → create a new PROJECT-scoped environment
     *
     * @param postmanEnvIds List of Postman environment IDs to pull
     * @param workspaceId Postman workspace ID (for cache invalidation)
     * @param conflictStrategy How to handle name collisions
     * @param includeDisabled Whether to include disabled Postman variables
     * @return [SyncResult] with succeeded and failed environments
     */
    suspend fun pullFromPostman(
        postmanEnvIds: List<String>,
        workspaceId: String,
        conflictStrategy: ConflictStrategy = ConflictStrategy.MERGE,
        includeDisabled: Boolean = false
    ): SyncResult {
        LOG.info("pullFromPostman: start, ${postmanEnvIds.size} env(s), workspace=$workspaceId, strategy=$conflictStrategy, includeDisabled=$includeDisabled")
        val token = getPostmanToken()
        if (!EnvironmentSyncLogic.isTokenConfigured(token)) {
            LOG.warn("pullFromPostman: aborted, Postman API token not configured")
            return SyncResult(
                succeeded = emptyList(),
                failed = postmanEnvIds.map { SyncFailure(it, "Postman API token is not configured") }
            )
        }

        val client = createCachedClient(token!!, workspaceId)
        val localEnvs = environmentService.getEnvironments()

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<SyncFailure>()

        for (envId in postmanEnvIds) {
            try {
                val postmanEnv = client.getEnvironment(envId)
                if (postmanEnv == null) {
                    failed.add(SyncFailure(envId, "Environment not found on Postman"))
                    continue
                }

                val localExisting = localEnvs.find { it.name == postmanEnv.name }
                val variables = EnvironmentSyncLogic.postmanVariablesToMap(postmanEnv.values, includeDisabled)

                val resolved = EnvironmentSyncLogic.resolveConflict(
                    localExisting, variables, postmanEnv.name, conflictStrategy
                )

                if (resolved == null) {
                    // SKIP strategy — not a failure
                    LOG.info("pullFromPostman: skipping environment '${postmanEnv.name}' (conflict)")
                    continue
                }

                if (localExisting != null) {
                    environmentService.updateEnvironment(localExisting.name, resolved)
                } else {
                    environmentService.addEnvironment(resolved)
                }
                succeeded.add(postmanEnv.name)
            } catch (e: Exception) {
                LOG.warn("pullFromPostman: failed for environment ID '$envId'", e)
                failed.add(SyncFailure(envId, EnvironmentSyncLogic.handleApiError(e)))
            }
        }

        LOG.info("pullFromPostman: done, ${succeeded.size} succeeded, ${failed.size} failed")
        return SyncResult(succeeded = succeeded, failed = failed)
    }

    /**
     * Checks if a Postman API token is configured.
     *
     * Used internally by [EnvironmentSyncDialog] (same package) and exposed
     * via [isAvailable] to SPI callers.
     */
    fun hasPostmanToken(): Boolean {
        return EnvironmentSyncLogic.isTokenConfigured(getPostmanToken())
    }

    /** SPI exposure of [hasPostmanToken]. */
    override fun isAvailable(): Boolean = hasPostmanToken()

    /**
     * Lists all available Postman workspaces for the configured API token.
     *
     * This is a biz-logic method intended for UI components (e.g., [EnvironmentSyncDialog])
     * to fetch workspace data without constructing API clients directly.
     *
     * @return List of workspaces, or empty list if token is missing or request fails
     */
    suspend fun listWorkspaces(): List<Workspace> {
        val token = getPostmanToken()
        if (!EnvironmentSyncLogic.isTokenConfigured(token)) {
            return emptyList()
        }
        val client = createCachedClient(token!!, null)
        return client.listWorkspaces(useCache = true)
    }

    /**
     * Lists all Postman environments in the given workspace.
     *
     * This is a biz-logic method intended for UI components (e.g., [EnvironmentSyncDialog])
     * to fetch environment data without constructing API clients directly.
     *
     * @param workspaceId Target workspace ID
     * @return List of Postman environment summaries, or empty list on failure
     */
    suspend fun listPostmanEnvironments(workspaceId: String): List<PostmanEnvironmentInfo> {
        val token = getPostmanToken()
        if (!EnvironmentSyncLogic.isTokenConfigured(token)) {
            return emptyList()
        }
        val client = createCachedClient(token!!, workspaceId)
        return client.listEnvironments(workspaceId, useCache = true)
    }

    /**
     * Shows the sync dialog and executes the sync operation on a background thread.
     *
     * This is the single entry point for environment sync from UI. It handles:
     * 1. Opening the [EnvironmentSyncDialog] with the current active environment pre-selected
     * 2. Running the sync on a background [ProgressManager] task
     * 3. Showing result notifications
     * 4. Invoking [onPullComplete] after a successful pull (e.g., to refresh UI)
     *
     * @param mode PUSH or PULL
     * @param onPullComplete callback invoked on EDT after a successful pull
     */
    override fun showSyncDialogAndExecute(
        mode: EnvironmentSyncSupport.SyncMode,
        onPullComplete: (() -> Unit)?
    ) {
        val currentEnv = environmentService.getActiveEnvironmentName()
        val dialog = EnvironmentSyncDialog(project, mode, currentEnv)
        if (!dialog.showAndGet()) return

        val selectedData = dialog.getSelectedEnvData()
        if (selectedData.isEmpty()) return

        val workspaceId = dialog.getSelectedWorkspaceId() ?: return

        val title = when (mode) {
            EnvironmentSyncSupport.SyncMode.PUSH -> "Pushing environments to Postman"
            EnvironmentSyncSupport.SyncMode.PULL -> "Pulling environments from Postman"
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title) {
            override fun run(indicator: ProgressIndicator) {
                val result = runBlocking {
                    when (mode) {
                        EnvironmentSyncSupport.SyncMode.PUSH -> {
                            val names = selectedData.filterIsInstance<String>()
                            pushToPostman(names, workspaceId)
                        }

                        EnvironmentSyncSupport.SyncMode.PULL -> {
                            val ids = selectedData.filterIsInstance<String>()
                            pullFromPostman(
                                ids, workspaceId,
                                dialog.getConflictStrategy(),
                                dialog.isIncludeDisabled()
                            )
                        }
                    }
                }
                showSyncNotification(result, mode)

                if (mode == EnvironmentSyncSupport.SyncMode.PULL && result.succeeded.isNotEmpty()) {
                    SwingUtilities.invokeLater { onPullComplete?.invoke() }
                }
            }
        })
    }

    /**
     * Shows notifications for a sync result.
     */
    private fun showSyncNotification(
        result: SyncResult,
        mode: EnvironmentSyncSupport.SyncMode
    ) {
        val direction = when (mode) {
            EnvironmentSyncSupport.SyncMode.PUSH -> "pushed"
            EnvironmentSyncSupport.SyncMode.PULL -> "pulled"
        }
        if (result.succeeded.isNotEmpty()) {
            NotificationUtils.notifyInfo(
                project, "Environment Sync",
                "${result.succeeded.size} environment(s) $direction successfully: ${result.succeeded.joinToString()}"
            )
        }
        if (result.failed.isNotEmpty()) {
            val failures = result.failed.joinToString("; ") { "${it.name}: ${it.error}" }
            NotificationUtils.notifyWarning(
                project, "Environment Sync",
                "Failed to $direction ${result.failed.size} environment(s): $failures"
            )
        }
    }

    private fun getPostmanToken(): String? {
        return project.settings<PostmanSettings>().postmanToken
    }

    private fun createCachedClient(token: String, workspaceId: String?): CachedPostmanApiClient {
        val httpClient = HttpClientProvider.getInstance(project).getClient()
        val postmanClient = PostmanApiClient(token, workspaceId = workspaceId, httpClient = httpClient)
        return postmanClient.asCached()
    }
}

enum class ConflictStrategy {
    REPLACE,
    MERGE,
    SKIP
}

data class SyncResult(
    val succeeded: List<String>,
    val failed: List<SyncFailure>
)

data class SyncFailure(
    val name: String,
    val error: String
)
