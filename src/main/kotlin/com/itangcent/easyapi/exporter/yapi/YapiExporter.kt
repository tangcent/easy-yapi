package com.itangcent.easyapi.exporter.yapi

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.ApiExporter
import com.itangcent.easyapi.exporter.model.*
import com.itangcent.easyapi.psi.helper.ApiMetadataResolver
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.util.ide.ModuleHelper

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
        val clientProvider = DefaultYapiApiClientProvider(project)
        runCatching { clientProvider.init() }
            .onFailure { return ExportResult.Error(it.message ?: "Export failed: $it") }

        val serverUrl = clientProvider.serverUrl
        val settings = settingBinder.read()
        val formatter = YapiFormatter(
            reqBodyJson5 = settings.yapiReqBodyJson5,
            resBodyJson5 = settings.yapiResBodyJson5
        )
        val engine = RuleEngine.getInstance(project)

        var successCount = 0
        var failCount = 0
        val errors = mutableListOf<String>()
        val exportedCarts = mutableMapOf<String, String>() // cartName -> cartUrl

        val indicator = context.indicator
        val totalEndpoints = context.endpointsToExport.size
        var processedCount = 0

        // Fire yapi.export.before once before the export loop
        engine.evaluate(RuleKeys.YAPI_EXPORT_BEFORE)

        val docHelper = StandardDocHelper.getInstance(project)
        val metadataResolver = ApiMetadataResolver(engine, docHelper)

        for (endpoint in context.endpointsToExport) {
            indicator?.checkCanceled()
            indicator?.text = endpoint.name ?: endpoint.path
            indicator?.fraction = processedCount.toDouble() / totalEndpoints

            try {
                val module = read {
                    val psiMethod = endpoint.sourceMethod
                    val psiClass = endpoint.sourceClass
                    val ruleModule = when {
                        psiMethod != null -> metadataResolver.resolveModule(psiMethod)
                        psiClass != null -> metadataResolver.resolveModule(psiClass)
                        else -> null
                    }
                    ruleModule?.takeIf { it.isNotBlank() }
                        ?: psiClass?.let { ModuleHelper.resolveModule(it)?.name }
                        ?: project.name
                }

                val client = clientProvider.getYapiApiClient(
                    module = module,
                    selectedToken = context.outputConfig.yapiOptions?.selectedToken
                )
                if (client == null) {
                    failCount++
                    errors.add("${endpoint.name}: No valid token for module '$module'")
                    processedCount++
                    continue
                }

                val folderName = endpoint.folder ?: "anonymous"
                val catId = client.findOrCreateCart(folderName).getOrNull()
                if (catId == null) {
                    failCount++
                    errors.add("${endpoint.name}: Failed to resolve cart '$folderName'")
                    processedCount++
                    continue
                }

                val yapiDoc = formatter.format(endpoint)
                val psiElement = endpoint.sourceMethod ?: endpoint.sourceClass

                psiElement?.let {
                    engine.evaluate(RuleKeys.YAPI_SAVE_BEFORE, it) { ctx ->
                        ctx.setExt("yapiInfo", yapiDoc)
                        ctx.setExt("endpoint", endpoint)
                    }
                }

                val result = client.uploadApi(yapiDoc, catId)

                psiElement?.let {
                    engine.evaluate(RuleKeys.YAPI_SAVE_AFTER, it) { ctx ->
                        ctx.setExt("yapiInfo", yapiDoc)
                        ctx.setExt("endpoint", endpoint)
                        ctx.setExt("result", result)
                    }
                }

                if (result.isSuccess) {
                    successCount++
                    if (catId !in exportedCarts) {
                        client.getProjectId().getOrNull()?.let { projectId ->
                            exportedCarts[folderName] = YapiUrls.cartUrl(serverUrl, projectId, catId)
                        }
                    }
                } else {
                    failCount++
                    result.errorMessage()?.let { errors.add("${endpoint.name}: $it") }
                }
            } catch (e: Exception) {
                failCount++
                errors.add("${endpoint.name}: ${e.message}")
            }
            processedCount++
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
    override suspend fun handleExportResult(
        project: Project,
        result: ExportResult.Success,
        outputConfig: OutputConfig
    ): Boolean {
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
