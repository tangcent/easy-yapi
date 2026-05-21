package com.itangcent.easyapi.exporter.channel

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.logging.IdeaLog

/**
 * Project-level service that discovers [ApiChannel] implementations
 * via the `com.itangcent.idea.plugin.easy-yapi.apiChannel` extension point.
 *
 * The extension point is application-scoped (no `area` attribute), so IntelliJ
 * creates a single shared instance per channel implementation. Project context
 * is passed through method parameters when needed.
 *
 * ## Usage
 *
 * ```kotlin
 * val channels = ApiChannelRegistry.getInstance(project).getAvailableChannels(endpoints)
 * ```
 *
 * @see ApiChannel
 */
@Service(Service.Level.PROJECT)
class ApiChannelRegistry(private val project: Project) : IdeaLog {

    companion object {
        fun getInstance(project: Project): ApiChannelRegistry = project.service()

        private val CHANNEL_EP = ExtensionPointName.create<ApiChannel>("com.itangcent.idea.plugin.easy-yapi.apiChannel")
    }

    /** Returns all registered [ApiChannel] implementations. */
    fun getAllChannels(): List<ApiChannel> = CHANNEL_EP.extensionList

    /** Returns channels that can handle the given [endpoints]. */
    fun getAvailableChannels(endpoints: List<ApiEndpoint>): List<ApiChannel> =
        getAllChannels().filter { it.isAvailableFor(endpoints) }

    /** Finds a channel by its unique [id], or `null` if not found. */
    fun getChannel(id: String): ApiChannel? =
        getAllChannels().firstOrNull { it.id == id }

    /** Returns channels that should be exposed as top-level IDE actions. */
    fun getActionChannels(): List<ApiChannel> =
        getAllChannels().filter { it.exposeAsAction }
}
