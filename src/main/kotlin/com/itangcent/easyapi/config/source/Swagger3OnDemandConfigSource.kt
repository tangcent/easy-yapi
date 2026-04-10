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
 * On-demand configuration source for OpenAPI 3.x / Swagger 3 annotations.
 *
 * This config source loads rules for extracting API documentation from OpenAPI 3.x annotations
 * (e.g., `@Operation`, `@Parameter`, `@Schema`, `@Tag`) when:
 * 1. The `swagger3Enable` setting is `true` (default)
 * 2. OpenAPI 3 annotations are detected in the project's classpath
 *
 * ## Configuration Files
 *
 * Loads configuration from:
 * - `third/swagger3.config` - OpenAPI 3.x annotation rules
 *
 * ## Supported Annotations
 *
 * | Annotation | Property | Config Rule |
 * |------------|----------|-------------|
 * | `@Operation` | `summary` | `api.name`, `method.doc` |
 * | `@Operation` | `description` | `method.doc` |
 * | `@Tag` | `name` | `api.tag`, `class.doc` |
 * | `@Parameter` | `description`, `required` | `param.doc`, `param.required` |
 * | `@Schema` | `description` | `field.doc`, `class.doc` |
 * | `@Hidden` | - | `ignore` |
 *
 * ## Example
 *
 * Given a controller with OpenAPI 3 annotations:
 * ```java
 * @Tag(name = "Order Management")
 * @RestController
 * @RequestMapping("/orders")
 * public class OrderController {
 *     @Operation(summary = "Get order by ID", description = "Retrieves an order")
 *     @GetMapping("/{id}")
 *     public Order getOrder(
 *         @Parameter(description = "Order ID", required = true) @PathVariable Long id
 *     ) {
 *         // ...
 *     }
 * }
 * ```
 *
 * The loaded configuration will extract:
 * - Class tag: "Order Management"
 * - API name: "Get order by ID"
 * - Method description: "Retrieves an order"
 * - Parameter description: "Order ID"
 * - Parameter required: `true`
 *
 * ## Thread Safety
 *
 * - [isEnabled] uses [readSync] for PSI access
 * - [loadConfig] is a suspend function safe for coroutine context
 *
 * @see OnDemandConfigSource
 * @see SwaggerOnDemandConfigSource
 * @see com.itangcent.easyapi.config.DefaultConfigReader
 */
class Swagger3OnDemandConfigSource(
    private val project: Project
) : OnDemandConfigSource() {

    companion object : IdeaLog {
        private const val SWAGGER3_CONFIG_RESOURCE = "third/swagger3.config"
        private const val SWAGGER3_ANNOTATION_CLASS = "io.swagger.v3.oas.annotations.Operation"
    }

    override val priority: Int = 3
    override val sourceId: String = "swagger3"

    private fun getSettings() = SettingBinder.getInstance(project).read()

    /**
     * Checks if OpenAPI 3.x support should be enabled.
     *
     * Returns `true` when:
     * 1. `swagger3Enable` setting is `true`
     * 2. `io.swagger.v3.oas.annotations.Operation` class is found in project dependencies
     *
     * @return `true` if OpenAPI 3 config should be loaded
     */
    override fun isEnabled(): Boolean {
        val settings = getSettings()
        if (!settings.swagger3Enable) {
            return false
        }
        return readSync {
            try {
                val facade = JavaPsiFacade.getInstance(project)
                facade.findClass(SWAGGER3_ANNOTATION_CLASS, GlobalSearchScope.allScope(project)) != null
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Loads OpenAPI 3.x configuration from bundled resources.
     *
     * @return Sequence of config entries for OpenAPI 3 annotation processing
     */
    override suspend fun loadConfig(): Sequence<ConfigEntry> {
        if (!isEnabled()) {
            return emptySequence()
        }

        val configText = loadResource(SWAGGER3_CONFIG_RESOURCE)
            ?: return emptySequence()

        val parser = ConfigTextParser(null)
        return parser.parse(configText, sourceId, null)
    }

    private fun loadResource(path: String): String? {
        return javaClass.classLoader.getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?.takeIf { it.isNotBlank() }
    }
}
