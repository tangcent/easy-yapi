package com.itangcent.easyapi.exporter.yapi

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.http.HttpClientProvider
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

class DefaultYapiApiClientProvider(
    private val project: Project
) : YapiApiClientProvider {

    private val settingsHelper = YapiSettingsHelper.getInstance(project)
    private val httpClient by lazy { HttpClientProvider.getInstance(project).getClient() }
    private val clientCache = ConcurrentHashMap<String, YapiApiClient>()

    private var _serverUrl: String = ""
    override val serverUrl: String get() = _serverUrl

    override suspend fun init() {
        _serverUrl = settingsHelper.resolveServerUrl()
            ?: throw IllegalStateException("YAPI server URL is not configured. Please configure it in Settings.")
        settingsHelper.resetPromptedModules()
    }

    override suspend fun getYapiApiClient(module: String, selectedToken: String?): YapiApiClient? {
        clientCache[module]?.let { return it }

        val token = selectedToken
            ?: settingsHelper.resolveToken(module) { candidate ->
                DefaultYapiApiClient(_serverUrl, candidate, httpClient).getProjectId().isSuccess
            }
            ?: return null

        return DefaultYapiApiClient(_serverUrl, token, httpClient)
            .also { clientCache[module] = it }
    }
}
