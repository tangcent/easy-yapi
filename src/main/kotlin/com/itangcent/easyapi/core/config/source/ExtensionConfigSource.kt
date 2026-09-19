package com.itangcent.easyapi.core.config.source

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.config.model.ConfigEntry
import com.itangcent.easyapi.core.config.model.ConfigSource
import com.itangcent.easyapi.core.config.parser.ConfigTextParser
import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.util.ide.ProjectClassAvailabilityService

/**
 * Configuration source for extension configurations.
 *
 * Provides configuration for various framework extensions
 * (Swagger, Jackson, Spring, etc.) based on user selection.
 *
 * This source has priority 3, higher than recommend but lower than built-in.
 *
 * @param selectedCodes The selected extension codes, or null for defaults
 * @param configTextParser Parser for configuration text
 */
class ExtensionConfigSource(
    private val project: Project,
    private val selectedCodes: Array<String>?,
    private val configTextParser: ConfigTextParser
) : ConfigSource {
    companion object : IdeaLog

    override val priority: Int = 3
    override val sourceId: String = "extension"

    /**
     * Collects configuration entries from selected extensions.
     *
     * The code-list grammar is not this class's business: [ExtensionConfigRegistry.enabledExtensions]
     * resolves the positives, the `-<code>` exclusions and the `defaultEnabled`
     * fallback, and this source only layers on the one question it can answer —
     * whether an extension's `on-class` is present on the project classpath.
     *
     * @return Sequence of parsed config entries, or empty sequence if no config is available
     */
    override suspend fun collect(): Sequence<ConfigEntry> {
        val classAvailabilityService = ProjectClassAvailabilityService.getInstance(project)

        val enabled = ExtensionConfigRegistry.enabledExtensions(selectedCodes ?: emptyArray())
            .filter { extension ->
                extension.onClass?.let { classAvailabilityService.hasClassInProject(it) } ?: true
            }

        val config = enabled.joinToString("\n") { it.content }

        LOG.info("Load extension config:\n $config\n")
        if (config.isBlank()) return emptySequence()
        return configTextParser.parse(config, sourceId, null)
    }
}
