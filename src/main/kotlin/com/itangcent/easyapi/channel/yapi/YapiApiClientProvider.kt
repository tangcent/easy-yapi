package com.itangcent.easyapi.channel.yapi

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.channel.yapi.model.YapiResponse
import com.itangcent.easyapi.core.http.HttpClientProvider
import com.itangcent.easyapi.core.logging.console
import java.util.concurrent.ConcurrentHashMap

/**
 * Creates and caches [YapiApiClient] instances per module for a single export operation.
 * Constructed per-operation with a [Project].
 */
interface YapiApiClientProvider {

    /** The resolved YAPI server URL. Available after [init]. */
    val serverUrl: String

    /**
     * Initializes the provider by resolving the server URL.
     * Must be called before [getYapiApiClient].
     * Throws [IllegalStateException] if the server URL is not configured.
     */
    suspend fun init()

    /**
     * Returns a [YapiApiClient] for the given module, resolving and validating the token as needed.
     * Result is cached — repeated calls for the same module return the same client.
     *
     * @param module The module name used to look up the token
     * @param selectedToken Optional pre-selected token that bypasses settings lookup
     * @return A ready-to-use client, or null if no valid token could be resolved
     */
    suspend fun getYapiApiClient(module: String, selectedToken: String? = null): YapiApiClient?
}

/**
 * @param headless no dialogs: settings only. A module whose token is missing, rejected by YApi or could not be
 *   checked gets no client, and [unavailableReason] tells the external caller which and what to do
 */
class DefaultYapiApiClientProvider(
    private val project: Project,
    private val headless: Boolean = false
) : YapiApiClientProvider {

    private val settingsHelper = YapiSettingsHelper.getInstance(project)
    private val httpClient by lazy { HttpClientProvider.getInstance(project).getClient() }
    private val clientCache = ConcurrentHashMap<String, YapiApiClient>()

    /** Headless only: why a module has no client, so its token is looked up once per export. */
    private val unavailable = ConcurrentHashMap<String, String>()

    private var _serverUrl: String = ""
    override val serverUrl: String get() = _serverUrl

    override suspend fun init() {
        _serverUrl = settingsHelper.resolveServerUrl(dumb = headless)
            ?: throw IllegalStateException(
                if (headless) {
                    "YAPI server URL is not configured. Ask the user to set \"Yapi Server\" under Settings → EasyYapi → Yapi."
                } else {
                    "YAPI server URL is not configured. Please configure it in Settings."
                }
            )
        // Only an interactive export prompts. The set belongs to the project service, so a headless export
        // leaves it alone for an interactive one that may be running.
        if (!headless) settingsHelper.resetPromptedModules()
    }

    override suspend fun getYapiApiClient(module: String, selectedToken: String?): YapiApiClient? {
        clientCache[module]?.let { return it }
        // Nobody to prompt: a token from the token table, once YApi accepted it, is used as a passed one.
        if (headless && selectedToken == null) return lookUpToken(module)?.let { getYapiApiClient(module, it) }

        val token = selectedToken
            ?: settingsHelper.resolveToken(module) { candidate -> verifyToken(candidate) }
            ?: return null

        return DefaultYapiApiClient(_serverUrl, token, httpClient, project = project)
            .also { clientCache[module] = it }
    }

    /**
     * Why [getYapiApiClient] gave no client for [module]: "No valid token for module '…'", or headless, whether
     * the token is missing, rejected by YApi or could not be checked, and what the external caller can do.
     */
    fun unavailableReason(module: String): String = unavailable[module] ?: "No valid token for module '$module'"

    /**
     * Checks the token passed for a headless export, once before any endpoint since every endpoint goes to its
     * project. Null when YApi accepted it, else why it cannot be used.
     */
    suspend fun checkPassedToken(token: String): String? {
        val check = verifyToken(token)
        return when {
            check.isSuccess -> null
            check.rejected -> "YApi rejected the token passed for this export, saying \"${check.errorMessage()}\". " +
                "Ask the user for a valid YApi project token, or call again without the token option to use the " +
                "IDE token table."

            else -> "Could not check the token passed for this export: YApi gave no usable answer " +
                "(${check.errorMessage()}). $SERVER_CHECK"
        }
    }

    /**
     * The token-table token of [module] once YApi accepted it, looked up without a prompt
     * ([YapiSettingsHelper.lookupToken]); otherwise null, and why is kept for [unavailableReason].
     */
    private suspend fun lookUpToken(module: String): String? {
        if (unavailable.containsKey(module)) return null
        val token = settingsHelper.lookupToken(module)
        val reason = if (token == null) {
            "No YApi token for '$module'. Pass the token option for this export, or ask " +
                "the user to add the line \"$module={token}\" under Settings → EasyYapi → Yapi → Tokens."
        } else {
            val check = verifyToken(token)
            if (check.isSuccess) return token
            if (check.rejected) {
                project.console.warn("YApi: token rejected for module '$module': ${check.errorMessage()}")
                "YApi rejected the token configured for '$module', saying \"${check.errorMessage()}\". " +
                    "Ask the user to update the line \"$module={token}\" under Settings → EasyYapi → Yapi → Tokens, " +
                    "or pass the token option."
            } else {
                project.console.warn("YApi: could not check the token for module '$module': ${check.errorMessage()}")
                "Could not check the token configured for '$module': YApi gave no usable " +
                    "answer (${check.errorMessage()}). $SERVER_CHECK"
            }
        }
        unavailable[module] = reason
        return null
    }

    /**
     * Checks [token] with the server. A failure says why, and is [YapiResponse.rejected] when YApi refused the
     * token; otherwise YApi could not be asked or gave no usable answer ([SERVER_CHECK]).
     */
    private suspend fun verifyToken(token: String): YapiResponse<String> =
        DefaultYapiApiClient(_serverUrl, token, httpClient, project = project).getProjectId()

    companion object {
        /**
         * What to do when a token could not be checked: YApi could not be asked or gave no usable answer, so the
         * server, not the token, is what to look at.
         */
        private const val SERVER_CHECK =
            "Ask the user to check \"Yapi Server\" under Settings → EasyYapi → Yapi and that the server is up."
    }
}
