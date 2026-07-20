package com.itangcent.easyapi.channel.yapi

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportMetadata
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.export.path
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.logging.console
import com.itangcent.easyapi.channel.yapi.YapiRuleKeys
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.channel.yapi.YapiSettings
import com.itangcent.easyapi.channel.yapi.YapiExportMode
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.util.ide.ModuleHelper
import com.itangcent.easyapi.channel.yapi.markdown.MarkdownRender

@Service(Service.Level.PROJECT)
class YapiExporter(private val project: Project) : IdeaLog {

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

        val yapiSettings = project.settings<YapiSettings>()
        val serverUrl = clientProvider.serverUrl
        val mockRules = MockRuleLoader.getInstance(project).getMockRules()
        val formatter = YapiFormatter(
            reqBodyJson5 = yapiSettings.yapiReqBodyJson5,
            resBodyJson5 = yapiSettings.yapiResBodyJson5,
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

        engine.evaluate(YapiRuleKeys.YAPI_EXPORT_BEFORE)

        val exportMode = runCatching { YapiExportMode.valueOf(yapiSettings.yapiExportMode) }
            .getOrDefault(YapiExportMode.ALWAYS_UPDATE)

        val updateConfirmationCache = mutableMapOf<YapiApiClient, UpdateConfirmation>()

        for (rawEndpoint in context.endpointsToExport) {
            // Attach YApi-specific endpoint metadata (tags/status/open) to the
            // extension carrier. The shared exporter no longer populates these.
            val endpoint = YapiMetadataPopulator.populate(rawEndpoint, engine)
            indicator?.checkCanceled()
            indicator?.text = endpoint.name ?: endpoint.path
            indicator?.fraction = processedCount.toDouble() / totalEndpoints

            try {
                val yapiProject = read {
                    val psiMethod = endpoint.sourceMethod
                    val psiClass = endpoint.sourceClass
                    val ruleProject = psiMethod?.let { YapiProjectResolver.resolveYapiProject(engine, it) }
                        ?: psiClass?.let { YapiProjectResolver.resolveYapiProject(engine, it) }
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
                    engine.evaluate(YapiRuleKeys.YAPI_SAVE_BEFORE, it) { ctx ->
                        ctx.setExt("yapiInfo", yapiDoc)
                        ctx.setExt("endpoint", endpoint)
                    }
                }

                val updateConfirmation = updateConfirmationCache.getOrPut(client) {
                    DefaultUpdateConfirmation(project, exportMode, client)
                }
                val result = client.uploadApi(yapiDoc, catId, updateConfirmation)

                psiElement?.let {
                    engine.evaluate(YapiRuleKeys.YAPI_SAVE_AFTER, it) { ctx ->
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
