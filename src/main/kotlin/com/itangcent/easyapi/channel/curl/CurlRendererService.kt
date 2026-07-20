package com.itangcent.easyapi.channel.curl

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.channel.spi.CurlRenderer
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.script.ScriptScope
import com.itangcent.easyapi.core.settings.settings

/**
 * Application-scoped implementation of [CurlRenderer] (Decision CO8).
 *
 * Bridges the `core.*` callers (`ScriptApiEndpoint.toCurl`,
 * `ApiDashboardPanel` copy-as-cURL action) to the concrete `channel.curl.*`
 * machinery (`CurlBuilder`, `CurlExportResolver`, `CurlScriptScopes`,
 * `CurlSettings`).
 *
 * ## Why application-scoped
 *
 * `ScriptApiEndpoint` may be constructed with `project = null` (unit tests,
 * headless rule eval). An application-scoped service is reachable without a
 * project, so the pure-format path ([format]) works in the null-project case.
 * Project-bound methods ([buildSync], [formatForCopy], [copyFromEdited]) take
 * `Project` as a parameter and delegate to the appropriate project-scoped
 * services.
 *
 * ## Thread safety
 *
 * Stateless except for delegations to project-scoped services. Each method
 * reads settings / project services per-call, so the service is safe to
 * share across projects.
 */
@Service(Service.Level.APP)
class CurlRendererService : CurlRenderer {

    override val defaultHost: String = CurlBuilder.DEFAULT_HOST

    override fun format(endpoint: ApiEndpoint, host: String): String {
        val effectiveHost = host.takeIf { it.isNotBlank() } ?: defaultHost
        return CurlBuilder.format(endpoint, effectiveHost)
    }

    override fun buildSync(
        project: Project?,
        endpoint: ApiEndpoint,
        host: String,
        runPreScripts: Boolean,
    ): String {
        val effectiveHost = host.takeIf { it.isNotBlank() } ?: defaultHost
        val options = if (project != null) {
            CurlBuildOptions(
                format = project.settings<CurlSettings>().toFormatOptions(),
                runPreScripts = runPreScripts,
                scopes = if (runPreScripts) CurlScriptScopes.resolveFolderAndClassScopes(endpoint) else emptyList(),
            )
        } else {
            CurlBuildOptions()
        }
        return CurlBuilder.buildSync(project, endpoint, effectiveHost, options)
    }

    override suspend fun formatForCopy(project: Project, endpoint: ApiEndpoint, host: String): String? {
        val effectiveHost = host.takeIf { it.isNotBlank() } ?: defaultHost
        return CurlExportResolver.getInstance(project).formatForCopy(endpoint, effectiveHost)
    }

    override fun copyFromEdited(project: Project): Boolean {
        return project.settings<CurlSettings>().copyFromEdited
    }

    override fun resolveFolderAndClassScopes(endpoint: ApiEndpoint): List<ScriptScope> {
        return CurlScriptScopes.resolveFolderAndClassScopes(endpoint)
    }
}
