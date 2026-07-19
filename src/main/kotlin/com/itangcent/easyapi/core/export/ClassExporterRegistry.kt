package com.itangcent.easyapi.core.export

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.internal.PluginInfo.PLUGIN_ID
import com.itangcent.easyapi.core.logging.IdeaLog

/**
 * Project-level service that discovers [ClassExporter] implementations
 * via the `com.itangcent.idea.plugin.easy-yapi.classExporter` extension point.
 *
 * The extension point is declared with `area="IDEA_PROJECT"`, so IntelliJ
 * creates a separate exporter instance per project and injects the [Project]
 * constructor parameter automatically.
 *
 * ## Usage
 *
 * ```kotlin
 * val exporters = ClassExporterRegistry.getInstance(project).getEnabledExporters()
 * ```
 *
 * @see ClassExporter
 */
@Service(Service.Level.PROJECT)
class ClassExporterRegistry(private val project: Project) : IdeaLog {

    companion object {
        fun getInstance(project: Project): ClassExporterRegistry = project.service()

        private val CLASS_EXPORTER_EP =
            ExtensionPointName.create<ClassExporter>("$PLUGIN_ID.classExporter")
    }

    /**
     * Returns all registered [ClassExporter] instances for this project.
     * Each instance is created by IntelliJ with the [Project] constructor parameter injected.
     */
    fun getAllExporters(): List<ClassExporter> =
        CLASS_EXPORTER_EP.getExtensions(project).toList()

    /**
     * Returns only the [ClassExporter] instances that are enabled for this project.
     * Delegates to each exporter's [ClassExporter.isEnabled] method.
     */
    suspend fun getEnabledExporters(): List<ClassExporter> =
        getAllExporters().filter { it.isEnabled() }
}
