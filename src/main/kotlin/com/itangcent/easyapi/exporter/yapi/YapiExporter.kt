package com.itangcent.easyapi.exporter.yapi

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportMetadata
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.path
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.logging.console
import com.itangcent.easyapi.psi.helper.DocMetadataResolver
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.YapiExportMode
import com.itangcent.easyapi.util.ide.ModuleHelper
import com.itangcent.easyapi.util.markdown.MarkdownRender

@Service(Service.Level.PROJECT)
class YapiExporter(private val project: Project) : IdeaLog {

    private val settingBinder by lazy { SettingBinder.getInstance(project) }
    private val console get() = project.console

    companion object {
        fun getInstance(project: Project): YapiExporter {
            return project.getService(YapiExporter::class.java)
        }
    }

    suspend fun export(context: ExportContext, selectedToken: String? = null): ExportResult {
        LOG.info("YapiExporter.export: start. endpoints=${context.endpointsToExport.size}")
        val clientProvider = DefaultYapiApiClientProvider(project)
        runCatching { clientProvider.init() }
            .onFailure {
                LOG.warn("YapiExporter.export: clientProvider.init failed", it)
                return ExportResult.Error(it.message ?: "Export failed: $it")
            }

        val serverUrl = clientProvider.serverUrl
        val settings = settingBinder.read()
        val mockRules = MockRuleLoader.getInstance(project).getMockRules()
        val formatter = YapiFormatter(
            reqBodyJson5 = settings.yapiReqBodyJson5,
            resBodyJson5 = settings.yapiResBodyJson5,
            mockRules = mockRules,
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
                    val msg = "${endpoint.name}: No valid token for module '$yapiProject'"
                    errors.add(msg)
                    console.warn(msg)
                    processedCount++
                    continue
                }

                val folderName = endpoint.folder ?: "anonymous"
                val catId = client.findOrCreateCart(folderName).getOrNull()
                if (catId == null) {
                    failCount++
                    val msg = "${endpoint.name}: Failed to resolve cart '$folderName'"
                    errors.add(msg)
                    console.warn(msg)
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
                    val msg = "${endpoint.name}: ${result.errorMessage()}"
                    errors.add(msg)
                    console.warn(msg)
                }
            } catch (e: Exception) {
                failCount++
                val msg = "${endpoint.name}: ${e.message}"
                errors.add(msg)
                console.warn("YapiExporter.export: endpoint failed", e)
            }
            processedCount++
        }

        val metadata = if (exportedCarts.isNotEmpty()) YapiExportMetadata(exportedCarts) else null

        val exportResult = when {
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
        LOG.info("YapiExporter.export: done. success=$successCount fail=$failCount")
        return exportResult
    }
}

class YapiExportMetadata(
    val cartLinks: Map<String, String>
) : ExportMetadata {
    override fun formatDisplay(): String {
        return cartLinks.entries.joinToString("\n") { (name, url) -> "$name: $url" }
    }
}
