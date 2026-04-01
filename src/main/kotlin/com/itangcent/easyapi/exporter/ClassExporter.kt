package com.itangcent.easyapi.exporter

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.exporter.model.ApiEndpoint

/**
 * Interface for exporting API endpoints from a PSI class.
 *
 * Implementations analyze class structures (like Spring controllers)
 * and extract API endpoint information.
 *
 * ## Implementations
 * - [SpringMvcClassExporter] - Exports Spring MVC controllers
 * - [JaxRsClassExporter] - Exports JAX-RS resources
 *
 * @see ApiEndpoint for the extracted endpoint model
 */
interface ClassExporter {
    /**
     * Exports API endpoints from the given PSI class.
     *
     * @param psiClass The class to analyze
     * @return List of discovered API endpoints
     */
    suspend fun export(psiClass: PsiClass): List<ApiEndpoint>
}
