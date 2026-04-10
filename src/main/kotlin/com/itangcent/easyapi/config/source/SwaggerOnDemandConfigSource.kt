package com.itangcent.easyapi.config.source

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.settings.SettingBinder

/**
 * On-demand configuration source for Swagger 2.x annotations.
 *
 * This config source loads rules for extracting API documentation from Swagger 2.x annotations
 * (e.g., `@Api`, `@ApiOperation`, `@ApiParam`, `@ApiModel`) when:
 * 1. The `swaggerEnable` setting is `true` (default)
 * 2. Swagger annotations are detected in the project's classpath
 *
 * ## Configuration Files
 *
 * Loads configuration from:
 * - `third/swagger.config` - Core Swagger annotation rules
 * - `third/swagger.advanced.config` - Advanced rules for complex scenarios
 *
 * ## Supported Annotations
 *
 * | Annotation | Property | Config Rule |
 * |------------|----------|-------------|
 * | `@Api` | `value`, `tags` | `class.doc` |
 * | `@ApiOperation` | `value` | `method.doc` |
 * | `@ApiParam` | `value`, `defaultValue` | `param.doc`, `param.default.value` |
 * | `@ApiModel` | `value` | `class.doc` |
 * | `@ApiModelProperty` | `value` | `field.doc` |
 *
 * ## Example
 *
 * Given a controller with Swagger annotations:
 * ```java
 * @Api(tags = "User Management")
 * @RestController
 * public class UserController {
 *     @ApiOperation(value = "Get user by ID")
 *     @GetMapping("/users/{id}")
 *     public User getUser(@ApiParam(value = "User ID", defaultValue = "1") @PathVariable Long id) {
 *         // ...
 *     }
 * }
 * ```
 *
 * The loaded configuration will extract:
 * - Class description: "User Management"
 * - Method description: "Get user by ID"
 * - Parameter description: "User ID"
 * - Parameter default value: "1"
 *
 * ## Thread Safety
 *
 * - [isEnabled] uses [readSync] for PSI access
 * - [loadConfig] is a suspend function safe for coroutine context
 *
 * @see OnDemandConfigSource
 * @see Swagger3OnDemandConfigSource
 * @see com.itangcent.easyapi.config.DefaultConfigReader
 */
class SwaggerOnDemandConfigSource(
    private val project: Project
) : OnDemandConfigSource(), IdeaLog {

    companion object {
        private const val SWAGGER_CONFIG_RESOURCE = "third/swagger.config"
        private const val SWAGGER_ADVANCED_CONFIG_RESOURCE = "third/swagger.advanced.config"
        private const val SWAGGER_ANNOTATION_CLASS = "io.swagger.annotations.Api"
    }

    override val priority: Int = 3
    override val sourceId: String = "swagger"

    private fun getSettings() = SettingBinder.getInstance(project).read()

    /**
     * Checks if Swagger 2.x support should be enabled.
     *
     * Returns `true` when:
     * 1. `swaggerEnable` setting is `true`
     * 2. `io.swagger.annotations.Api` class is found in project dependencies
     *
     * @return `true` if Swagger config should be loaded
     */
    override fun isEnabled(): Boolean {
        val settings = getSettings()
        if (!settings.swaggerEnable) {
            return false
        }
        return readSync {
            try {
                val facade = JavaPsiFacade.getInstance(project)
                facade.findClass(SWAGGER_ANNOTATION_CLASS, GlobalSearchScope.allScope(project)) != null
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Loads Swagger 2.x configuration from bundled resources.
     *
     * Combines `swagger.config` and `swagger.advanced.config` into a single
     * configuration stream.
     *
     * @return Sequence of config entries for Swagger annotation processing
     */
    override suspend fun loadConfig(): Sequence<ConfigEntry> {
        if (!isEnabled()) {
            return emptySequence()
        }

        val swaggerConfig = loadResource(SWAGGER_CONFIG_RESOURCE)
        val swaggerAdvancedConfig = loadResource(SWAGGER_ADVANCED_CONFIG_RESOURCE)

        val combinedConfig = listOfNotNull(swaggerConfig, swaggerAdvancedConfig)
            .filter { it.isNotBlank() }
            .joinToString("\n")

        if (combinedConfig.isBlank()) {
            return emptySequence()
        }

        val parser = ConfigTextParser(null)
        return parser.parse(combinedConfig, sourceId, null)
    }

    private fun loadResource(path: String): String? {
        return javaClass.classLoader.getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?.takeIf { it.isNotBlank() }
    }
}
