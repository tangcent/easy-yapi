package com.itangcent.easyapi.exporter.yapi

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.ApiExporter
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportMetadata
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.http.HttpClientProvider
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.psi.helper.ApiMetadataResolver
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.util.ide.ModuleHelper
import kotlinx.coroutines.withContext

/**
 * Exporter for uploading API endpoints to YAPI platform.
 * 
 * This exporter handles:
 * - Token resolution and validation per module
 * - Category (cart) creation and management
 * - Batch upload of endpoints organized by module and folder
 * - Rule engine hooks for customization
 * 
 * YAPI is an efficient API management platform that provides
 * documentation, testing, and mocking capabilities.
 */
@Service(Service.Level.PROJECT)
class YapiExporter(private val project: Project) : ApiExporter {

    /** The export format this exporter handles */
    override val format: ExportFormat = ExportFormat.YAPI

    private val settingBinder by lazy { SettingBinder.getInstance(project) }

    /**
     * Returns the singleton instance for the given project.
     */
    companion object {
        fun getInstance(project: Project): YapiExporter {
            return project.getService(YapiExporter::class.java)
        }
    }

    /**
     * Exports API endpoints to YAPI server.
     * 
     * Process:
     * 1. Groups endpoints by module and folder
     * 2. Resolves valid tokens for each module
     * 3. Creates categories (carts) as needed
     * 4. Uploads each endpoint with rule hooks
     * 
     * @param context The export context containing endpoints and configuration
     * @return Success with count and cart links, or error result
     */
    override suspend fun export(context: ExportContext): ExportResult {
        val settingsHelper = YapiSettingsHelper.getInstance(project)
        val serverUrl = settingsHelper.resolveServerUrl()
            ?: return ExportResult.Error("YAPI server URL is not configured. Please configure it in Settings.")
        val settings = settingBinder.read()

        settingsHelper.resetPromptedModules()

        val actionContext = context.actionContext ?: ActionContext.forProject(project)
        val httpClient = HttpClientProvider.getInstance(actionContext).getClient()
        val formatter = YapiFormatter(
            reqBodyJson5 = settings.yapiReqBodyJson5,
            resBodyJson5 = settings.yapiResBodyJson5
        )

        var successCount = 0
        var failCount = 0
        val errors = mutableListOf<String>()
        // cartName -> cartUrl, for the success notification
        val exportedCarts = mutableMapOf<String, String>()

        val engine = RuleEngine.getInstance(actionContext)

        val endpointsByModule = withContext(IdeDispatchers.ReadAction) {
            val docHelper = actionContext.instanceOrNull(DocHelper::class)
            val metadataResolver = if (docHelper != null) ApiMetadataResolver(engine, docHelper) else null
            context.endpointsToExport.groupBy { endpoint ->
                val psiMethod = endpoint.sourceMethod
                val psiClass = endpoint.sourceClass
                // Priority: module rule on method > module rule on class > IDE module name > project name
                val ruleModule = when {
                    psiMethod != null && metadataResolver != null -> metadataResolver.resolveModule(psiMethod)
                    psiClass != null && metadataResolver != null -> metadataResolver.resolveModule(psiClass)
                    else -> null
                }
                ruleModule?.takeIf { it.isNotBlank() }
                    ?: psiClass?.let { ModuleHelper.resolveModule(it)?.name }
                    ?: project.name
            }
        }

        val indicator = context.indicator
        val totalEndpoints = context.endpointsToExport.size
        var processedCount = 0

        // Fire yapi.export.before once before the export loop
        engine.evaluate(RuleKeys.YAPI_EXPORT_BEFORE)

        for ((module, endpoints) in endpointsByModule) {
            val resolvedToken = context.outputConfig.yapiOptions?.selectedToken
                ?: settingsHelper.resolveToken(module) { token ->
                    YapiApiClient(serverUrl, token, httpClient = httpClient).validateToken()
                }

            if (resolvedToken == null) {
                failCount += endpoints.size
                errors.add("No valid token for module '$module'")
                continue
            }

            val client = YapiApiClient(serverUrl, resolvedToken, httpClient = httpClient)
            val projectId = client.getProjectId()

            val endpointsByFolder = endpoints.groupBy { it.folder ?: "anonymous" }

            for ((folderName, folderEndpoints) in endpointsByFolder) {
                val catId = try {
                    client.findOrCreateCart(folderName)
                } catch (e: Exception) {
                    null
                }

                if (catId == null) {
                    for (endpoint in folderEndpoints) {
                        failCount++
                        errors.add("${endpoint.name}: Failed to resolve cart '$folderName'")
                    }
                    continue
                }

                var folderSuccess = false
                for (endpoint in folderEndpoints) {
                    indicator?.checkCanceled()
                    indicator?.text = endpoint.name ?: endpoint.path
                    indicator?.fraction = processedCount.toDouble() / totalEndpoints
                    try {
                        val yapiDoc = formatter.format(endpoint)

                        // Fire yapi.save.before hook
                        val psiElement = endpoint.sourceMethod ?: endpoint.sourceClass
                        if (psiElement != null) {
                            engine.evaluate(RuleKeys.YAPI_SAVE_BEFORE, psiElement) { ctx ->
                                ctx.setExt("yapiInfo", yapiDoc)
                                ctx.setExt("endpoint", endpoint)
                            }
                        }

                        val result = client.uploadApi(yapiDoc, catId)

                        // Fire yapi.save.after hook
                        if (psiElement != null) {
                            engine.evaluate(RuleKeys.YAPI_SAVE_AFTER, psiElement) { ctx ->
                                ctx.setExt("yapiInfo", yapiDoc)
                                ctx.setExt("endpoint", endpoint)
                                ctx.setExt("result", result)
                            }
                        }

                        if (result.success) {
                            successCount++
                            folderSuccess = true
                        } else {
                            failCount++
                            result.message?.let { errors.add("${endpoint.name}: $it") }
                        }
                    } catch (e: Exception) {
                        failCount++
                        errors.add("${endpoint.name}: ${e.message}")
                    }
                    processedCount++
                }

                if (folderSuccess && projectId != null) {
                    val cartUrl = YapiUrls.cartUrl(serverUrl, projectId, catId)
                    exportedCarts[folderName] = cartUrl
                }
            }
        }

        val metadata = if (exportedCarts.isNotEmpty()) YapiExportMetadata(exportedCarts) else null

        return when {
            failCount == 0 && successCount > 0 -> ExportResult.Success(
                count = successCount,
                target = "$serverUrl (YAPI)",
                metadata = metadata
            )

            successCount == 0 && failCount > 0 -> ExportResult.Error(
                "All exports failed:\n${errors.take(5).joinToString("\n")}"
            )

            successCount > 0 -> ExportResult.Success(
                count = successCount,
                target = "$serverUrl (YAPI) - $failCount failed",
                metadata = metadata
            )

            else -> ExportResult.Error("No endpoints to export")
        }
    }

    /**
     * Handles the export result by showing a notification with cart links.
     * Each cart link opens in the browser when clicked.
     * 
     * @param project The IntelliJ project
     * @param result The successful export result
     * @return true if notification was shown
     */
    override suspend fun handleExportResult(project: Project, result: ExportResult.Success): Boolean {
        val metadata = result.metadata as? YapiExportMetadata ?: return false
        swing {
            val notification = com.intellij.notification.Notification(
                "EasyAPI Notifications",
                "Export to YAPI",
                "Exported ${result.count} endpoints to YAPI",
                com.intellij.notification.NotificationType.INFORMATION
            )
            for ((cartName, cartUrl) in metadata.cartLinks) {
                notification.addAction(object : com.intellij.notification.NotificationAction(cartName) {
                    override fun actionPerformed(
                        e: com.intellij.openapi.actionSystem.AnActionEvent,
                        notification: com.intellij.notification.Notification
                    ) {
                        com.intellij.ide.BrowserUtil.browse(cartUrl)
                    }
                })
            }
            com.intellij.notification.Notifications.Bus.notify(notification, project)
        }
        return true
    }
}

/**
 * Metadata for YAPI export results containing cart links.
 * 
 * @property cartLinks Map of cart name to YAPI URL
 */
class YapiExportMetadata(
    val cartLinks: Map<String, String>
) : ExportMetadata {
    override fun formatDisplay(): String {
        return cartLinks.entries.joinToString("\n") { (name, url) -> "$name: $url" }
    }
}
