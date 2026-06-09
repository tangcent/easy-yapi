package com.itangcent.easyapi.exporter.yapi

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportMetadata
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.path
import com.itangcent.easyapi.psi.helper.DocMetadataResolver
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.YapiExportMode
import com.itangcent.easyapi.util.ide.ModuleHelper
import com.itangcent.easyapi.util.markdown.MarkdownRender

@Service(Service.Level.PROJECT)
class YapiExporter(private val project: Project) {

    private val settingBinder by lazy { SettingBinder.getInstance(project) }

    companion object {
        fun getInstance(project: Project): YapiExporter {
            return project.getService(YapiExporter::class.java)
        }
    }

    suspend fun export(context: ExportContext, selectedToken: String? = null): ExportResult {
        val clientProvider = DefaultYapiApiClientProvider(project)
        runCatching { clientProvider.init() }
            .onFailure { return ExportResult.Error(it.message ?: "Export failed: $it") }

        val serverUrl = clientProvider.serverUrl
        val settings = settingBinder.read()
        val mockRules = MockRuleLoader.getInstance(project).getMockRules()
        val formatter = YapiFormatter(
            reqBodyJson5 = settings.yapiReqBodyJson5,
            resBodyJson5 = settings.yapiResBodyJson5,
            mockRules = mockRules,
            responseWrapperEnabled = settings.yapiResponseWrapperEnabled,
            responseWrapperTemplate = settings.yapiResponseWrapperTemplate,
            markdownRender = MarkdownRender.getInstance(project)
        )
        val engine = RuleEngine.getInstance(project)

        var successCount = 0
        var failCount = 0
        val errors = mutableListOf<String>()
        val exportedCarts = mutableMapOf<String, String>()

        val indicator = context.indicator
        val totalEndpoints = context.endpointsToExport.size
        var processedCount = 0

        engine.evaluate(RuleKeys.YAPI_EXPORT_BEFORE)

        val metadataResolver = DocMetadataResolver.getInstance(project)

        val exportMode = runCatching { YapiExportMode.valueOf(settings.yapiExportMode) }
            .getOrDefault(YapiExportMode.ALWAYS_UPDATE)

        val updateConfirmationCache = mutableMapOf<YapiApiClient, UpdateConfirmation>()

        for (endpoint in context.endpointsToExport) {
            indicator?.checkCanceled()
            indicator?.text = endpoint.name ?: endpoint.path
            indicator?.fraction = processedCount.toDouble() / totalEndpoints

            try {
                val yapiProject = read {
                    val psiMethod = endpoint.sourceMethod
                    val psiClass = endpoint.sourceClass
                    val ruleProject = psiMethod?.let { metadataResolver.resolveYapiProject(it) }
                        ?: psiClass?.let { metadataResolver.resolveYapiProject(it) }
                    ruleProject?.takeIf { it.isNotBlank() }
                        ?: psiClass?.let { ModuleHelper.resolveModule(it)?.name }
                        ?: project.name
                }

                val client = clientProvider.getYapiApiClient(
                    module = yapiProject,
                    selectedToken = selectedToken
                )
                if (client == null) {
                    failCount++
                    errors.add("${endpoint.name}: No valid token for module '$yapiProject'")
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

                val yapiDoc = formatter.formatWithMock(endpoint)
                val psiElement = endpoint.sourceMethod ?: endpoint.sourceClass

                psiElement?.let {
                    engine.evaluate(RuleKeys.YAPI_SAVE_BEFORE, it) { ctx ->
                        ctx.setExt("yapiInfo", yapiDoc)
                        ctx.setExt("endpoint", endpoint)
                    }
                }

                val updateConfirmation = updateConfirmationCache.getOrPut(client) {
                    DefaultUpdateConfirmation(project, exportMode, client)
                }
                val result = client.uploadApi(yapiDoc, catId, updateConfirmation)

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
}

class YapiExportMetadata(
    val cartLinks: Map<String, String>
) : ExportMetadata {
    override fun formatDisplay(): String {
        return cartLinks.entries.joinToString("\n") { (name, url) -> "$name: $url" }
    }
}
