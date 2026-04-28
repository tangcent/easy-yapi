package com.itangcent.easyapi.config.source

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.model.ConfigSource
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.util.ide.ProjectClassAvailabilityService

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
     * This method builds configuration text from extension codes and parses it into config entries.
     * Extensions with `on-class` conditions are filtered based on project classpath availability.
     *
     * @return Sequence of parsed config entries, or empty sequence if no config is available
     */
    override suspend fun collect(): Sequence<ConfigEntry> {
        val classAvailabilityService = ProjectClassAvailabilityService.getInstance(project)

        // Filter extensions by onClass availability
        val availableExtensions = ExtensionConfigRegistry.allExtensions().filter { extension ->
            extension.onClass?.let { classAvailabilityService.hasClassInProject(it) } ?: true
        }

        // Build config from available extensions
        val codes = selectedCodes ?: emptyArray()
        val config = if (codes.isEmpty()) {
            availableExtensions
                .filter { it.defaultEnabled }
                .joinToString("\n") { it.content }
        } else {
            val set = codes.toSet()
            availableExtensions
                .filter { set.contains(it.code) || (it.defaultEnabled && !set.contains("-${it.code}")) }
                .joinToString("\n") { it.content }
        }

        LOG.info("Load extension config:\n $config\n")
        if (config.isBlank()) return emptySequence()
        return configTextParser.parse(config, sourceId, null)
    }
}
