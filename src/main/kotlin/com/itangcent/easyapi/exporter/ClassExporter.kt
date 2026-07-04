package com.itangcent.easyapi.exporter

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.exporter.model.ApiEndpoint

/**
 * Extension point interface for discovering API endpoints from source code.
 *
 * Implementations are registered via the `com.itangcent.idea.plugin.easy-api.classExporter`
 * extension point in `plugin.xml` with `area="IDEA_PROJECT"`, so IntelliJ creates
 * a separate instance per project and injects the [Project] constructor parameter.
 *
 * ## Lifecycle
 *
 * 1. IntelliJ instantiates the exporter (per-project) via the extension point mechanism.
 * 2. [isEnabled] is called to check whether this exporter should participate in the current scan.
 * 3. [export] is called for each [PsiClass] that passes the recognizer filter.
 *
 * ## Implementing
 *
 * - Constructor must accept exactly one [com.intellij.openapi.project.Project] parameter
 *   (required by the `IDEA_PROJECT` area extension point).
 * - Override [isEnabled] to check project settings and class availability.
 * - Override [export] to extract [ApiEndpoint]s from a [PsiClass].
 *
 * @see ClassExporterRegistry for the registry that discovers and filters enabled exporters
 */
interface ClassExporter {

    /**
     * Human-readable name of the framework this exporter handles (e.g. "SpringMVC", "gRPC").
     */
    val frameworkName: String

    /**
     * Checks whether this exporter is enabled for the current project.
     *
     * Implementations should read settings and check class availability from the
     * [com.intellij.openapi.project.Project] instance injected via the constructor.
     *
     * @return `true` if this exporter should participate in API scanning
     */
    suspend fun isEnabled(): Boolean = true

    /**
     * Exports API endpoints from the given [PsiClass].
     *
     * Called only when [isEnabled] returns `true`.
     *
     * @param psiClass the class to scan for API endpoints
     * @return list of discovered [ApiEndpoint]s, or empty list if the class has none
     */
    suspend fun export(psiClass: PsiClass): List<ApiEndpoint>
}
