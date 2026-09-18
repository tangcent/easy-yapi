package com.itangcent.easyapi.channel.apipost

import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.channel.spi.ChannelOptionsPanel
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.export.isHttp
import com.itangcent.easyapi.core.ide.support.NotificationUtils
import com.itangcent.easyapi.core.internal.threading.background
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.internal.threading.swing
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.settings.ui.SettingsPanel
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlin.reflect.KClass

/**
 * [Channel] implementation that exports HTTP endpoints to ApiPost.
 *
 * HTTP-only: gRPC endpoints are filtered out before formatting and each skip is
 * logged at `info`. When nothing HTTP remains, `export` returns
 * [ExportResult.Error].
 *
 * Disabled by default and marked **beta** — the user opts in via
 * Settings → General → "Export Channels", then reopens Settings to reach the
 * ApiPost tab (same chicken-and-egg path as OpenAPI, see P1-4).
 *
 * ## Export flow
 *
 * 1. Partition endpoints into HTTP / gRPC; log gRPC skips; error out when no
 *    HTTP endpoints remain.
 * 2. Resolve **project id**: options panel → `apipost.project` rule → settings.
 * 3. Resolve **two different URLs** (they must never be confused — review
 *    finding E):
 *      - `apiBase` = `apipost.host` rule ?? settings — where ApiPost lives;
 *        only ever assembles `/open/apis/…` request URLs.
 *      - `docServerUrl` = `apipost.server.url` rule — the API *being
 *        documented*, written into the envelope's `config.host` / `base_path`.
 *      No fallback from one to the other.
 * 4. Fire `apipost.export.before`.
 * 5. Build the OpenAPI document (via the single [ApipostOpenApiBridge]),
 *    serialize it, and convert it to the ApiPost **native** envelope — that is
 *    the only shape the server accepts.
 * 6. **Push** when a token, a project id and an [ApipostApiClientFactory] are
 *    all available; otherwise **degrade to a file** (`apipost.json`) the user
 *    imports by hand (AC-3).
 *
 * ## Push availability
 *
 * [apiClientFactory] defaults to [DefaultApipostApiClientFactory], so a user who
 * has configured a token and a project id gets a real push. The file path is what
 * remains when either is missing — or when a test sets the factory to `null` to
 * exercise the fallback.
 *
 * The push contract was verified against a live token on 2026-09-17
 * (`REVIEW.md §6.9`): field names, required-per-verb sets, dedup semantics,
 * folder creation and the `$ref` remap the push needs. What is **not** verified
 * is whether ApiPost's web importer accepts the *file* product (GATE-B), which is
 * why the fallback is still described as the conservative path.
 *
 * ## Logging
 *
 * `LOG.info` for milestones, `LOG.warn` for recoverable failures; no
 * `LOG.error` (the orchestrator already surfaces failures — AC-9).
 */
class ApipostChannel : Channel, IdeaLog {

    /**
     * Builds the HTTP client for a push. Set to `null` to force the file fallback.
     *
     * Defaults to [DefaultApipostApiClientFactory]; tests replace it with a fake,
     * or null it out to exercise the degradation path without network access.
     */
    internal var apiClientFactory: ApipostApiClientFactory? = DefaultApipostApiClientFactory()

    override val id: String = "apipost"
    override val displayName: String = "ApiPost (Beta)"
    override val supportsGrpc: Boolean = false
    override val exposeAsAction: Boolean = true
    override val actionText: String = "Export to ApiPost"
    override val enabledByDefault: Boolean = false
    override val beta: Boolean = true
    override val settingsType: KClass<out Settings> = ApipostSettings::class

    override fun createOptionsPanel(project: Project): ChannelOptionsPanel = ApipostOptionsPanel(project)

    override fun createSettingsPanel(project: Project): SettingsPanel<*>? = ApipostSettingsPanel(project)

    override fun configFiles(): List<String> = listOf("apipost")

    override fun ruleKeys(): List<RuleKey<*>> = RuleKey.collectFrom(ApipostRuleKeys)

    override suspend fun export(context: ExportContext): ExportResult {
        LOG.info("ApipostChannel.export: endpoints=${context.endpointsToExport.size}")
        val project = context.project

        // 1. HTTP only — ApiPost has no gRPC representation.
        val (httpEndpoints, grpcEndpoints) = context.endpointsToExport.partition { it.isHttp }
        grpcEndpoints.forEach { ep ->
            LOG.info("Skipping gRPC endpoint (ApiPost does not represent gRPC): ${ep.name ?: "<unknown>"}")
        }
        if (httpEndpoints.isEmpty()) {
            return ExportResult.Error("No HTTP endpoints to export")
        }

        val settings = project.settings<ApipostSettings>()
        val typed = context.channelConfig as? ApipostConfig ?: ApipostConfig()
        val engine = RuleEngine.getInstance(project)

        // 2. Project id: options panel > apipost.project rule > settings.
        val ruleElement = httpEndpoints.firstNotNullOfOrNull { it.sourceMethod }
            ?: httpEndpoints.firstNotNullOfOrNull { it.sourceClass }
        val projectId = typed.projectId?.takeIf { it.isNotBlank() }
            ?: resolveStringRule(engine, ApipostRuleKeys.APIPOST_PROJECT, ruleElement)
            ?: settings.apipostProjectId?.takeIf { it.isNotBlank() }

        // 3. Two URLs, deliberately never derived from each other (finding E).
        val apiBase = resolveStringRule(engine, ApipostRuleKeys.APIPOST_HOST, ruleElement)
            ?: settings.apipostServer?.takeIf { it.isNotBlank() }
            ?: ApipostSettings.APIPOST_OPEN_HOST
        val docServerUrl = resolveStringRule(engine, ApipostRuleKeys.APIPOST_SERVER_URL, ruleElement)

        // 4. Batch-level hook.
        engine.evaluate(ApipostRuleKeys.APIPOST_EXPORT_BEFORE)

        // 5. OpenAPI is only an intermediate representation — the server has
        //    never accepted it (finding G), so it is converted immediately.
        val oasDocument = ApipostOpenApiBridge.buildDocument(
            project = project,
            endpoints = httpEndpoints,
            infoTitle = project.name,
            infoVersion = DEFAULT_INFO_VERSION,
            documentServerUrl = docServerUrl,
        )
        val nativeDocument = ApipostNativeFormatter.format(
            ApipostOpenApiBridge.toJson(oasDocument),
            httpEndpoints,
            ApipostNativeOptions.of(projectId, project.name, docServerUrl),
        )
        val content = ApipostNativeSerializer.toJson(nativeDocument)

        // 6. Push, or degrade to a file.
        val token = typed.selectedToken?.takeIf { it.isNotBlank() }
            ?: settings.apipostToken?.takeIf { it.isNotBlank() }
        val factory = apiClientFactory

        if (token == null || projectId == null || factory == null) {
            LOG.info(
                "ApipostChannel.export: file fallback " +
                        "(token=${token != null}, projectId=${projectId != null}, client=${factory != null})"
            )
            return ExportResult.Success(
                count = httpEndpoints.size,
                target = "ApiPost file",
                metadata = ApipostExportMetadata(
                    content = content,
                    serverUrl = apiBase,
                    projectId = projectId,
                    fileExport = true,
                ),
            )
        }

        val client = factory.create(apiBase, token, project)
        val outcome = ApipostExporter.getInstance(project)
            .push(client, projectId, nativeDocument, context.indicator)
        outcome.failure?.let { failure ->
            LOG.warn("ApipostChannel.export: push failed — $failure")
            return ExportResult.Error(failure)
        }

        return ExportResult.Success(
            count = outcome.created + outcome.updated,
            target = "$apiBase (ApiPost)",
            metadata = ApipostExportMetadata(
                content = content,
                serverUrl = apiBase,
                projectId = projectId,
                fileExport = false,
                created = outcome.created,
                updated = outcome.updated,
            ),
        )
    }

    override suspend fun handleResult(
        project: Project,
        result: ExportResult.Success,
        config: ChannelConfig,
    ): Boolean {
        val metadata = result.metadata as? ApipostExportMetadata ?: return false

        if (!metadata.fileExport) {
            swing {
                NotificationUtils.notifyInfo(
                    project,
                    "Export API",
                    "已导出 ${result.count} 个接口到 ApiPost" +
                            "（新建 ${metadata.created}，更新 ${metadata.updated}）",
                )
            }
            return true
        }

        val targetFile = resolveTargetFile(project, config, DEFAULT_FILE_NAME)
            ?: throw CancellationException("User cancelled file selection")

        background { targetFile.writeText(metadata.content) }
        LOG.info("ApiPost document exported to ${targetFile.absolutePath}")

        swing {
            Messages.showInfoMessage(
                project,
                "已导出 ${result.count} 个接口到 ${targetFile.absolutePath}\n" +
                        "请到 ApiPost → 导入，选择「ApiPost 格式」并上传该文件。",
                "Export API",
            )
        }
        return true
    }

    /**
     * Evaluates a string rule with an element context when one is available and
     * with an empty context otherwise — the ApiPost keys declare both `CLASS`
     * and `EMPTY` as valid contexts.
     *
     * @requires ReadAction context (rule scripts may reach PSI through `helper`)
     */
    private suspend fun resolveStringRule(
        engine: RuleEngine,
        key: RuleKey.StringKey,
        element: com.intellij.psi.PsiElement?,
    ): String? = read {
        element?.let { engine.evaluate(key, it) }?.takeIf { it.isNotBlank() }
            ?: engine.evaluate(key)?.takeIf { it.isNotBlank() }
    }

    /**
     * Resolves the target file from a [ChannelConfig.FileConfig].
     *
     * Mirrors `OpenApiChannel.resolveTargetFile`: `outputDir`/`fileName` live on
     * the shared [ChannelConfig.FileConfig] rather than on [ApipostConfig],
     * which cannot extend that final data class (P0-2).
     *
     * @return `null` when the user must be prompted.
     */
    private suspend fun resolveTargetFile(
        project: Project,
        config: ChannelConfig?,
        defaultFileName: String,
    ): File? {
        val fileConfig = config as? ChannelConfig.FileConfig
        val outputDir = fileConfig?.outputDir
        val fileName = fileConfig?.fileName
        if (!outputDir.isNullOrBlank()) {
            val dir = File(outputDir)
            if (!dir.exists()) dir.mkdirs()
            val name = when {
                fileName.isNullOrBlank() -> defaultFileName
                fileName.contains('.') -> fileName
                else -> "$fileName.json"
            }
            return File(dir, name)
        }
        return selectTargetFile(project, defaultFileName)
    }

    private suspend fun selectTargetFile(project: Project, defaultFileName: String): File? = swing {
        val descriptor = FileSaverDescriptor(
            "Save ApiPost Document",
            "Choose where to save the ApiPost import file",
        )
        val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val wrapper: VirtualFileWrapper? = saver.save(null as VirtualFile?, defaultFileName)
        wrapper?.file
    }

    companion object {
        /** Default file name for the degraded (no-token) export path. */
        const val DEFAULT_FILE_NAME = "apipost.json"

        private const val DEFAULT_INFO_VERSION = "1.0.0"
    }
}
