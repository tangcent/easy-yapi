package com.itangcent.easyapi.channel.openapi

import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.channel.spi.ChannelOptionsPanel
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.export.isHttp
import com.itangcent.easyapi.core.internal.threading.background
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
 * [Channel] implementation for exporting HTTP API endpoints to an OpenAPI 3.0.3
 * document.
 *
 * HTTP-only: gRPC endpoints are filtered out before formatting and a
 * per-endpoint skip is logged at `info`. When the filtered set is empty,
 * `export` returns `ExportResult.Error("No HTTP endpoints to export")`.
 *
 * Disabled by default — the user must opt in via Settings → General →
 * "Export Channels". Marked as **beta**: the channel is functional but may
 * have rough edges; `displayName` carries a "(Beta)" suffix to signal this
 * to the user.
 *
 * ## Export flow
 *
 * 1. Partition `endpointsToExport` into HTTP / gRPC. Log each gRPC skip at
 *    `info`. Error out if no HTTP endpoints remain.
 * 2. Resolve the effective [OpenApiOutputFormat]. Precedence:
 *    options-panel > persistent settings > built-in default (`ALWAYS_ASK`).
 *    When `ALWAYS_ASK` is selected, [promptFormat] prompts the user on EDT
 *    — throws [CancellationException] on cancel.
 * 3. Resolve the rule-resolved [OpenApiEnvelope] via
 *    [resolveRuleBasedEnvelope] — document-level metadata (`info.title`,
 *    `info.version`, `info.description`, `server.url`) come from rule scripts
 *    only. Built-in defaults: `infoTitle = projectName ?: "API"`,
 *    `infoVersion = "1.0.0"`, others `null`.
 * 4. Build the [OpenApiDocument] via [OpenApiFormatter] (pure).
 * 5. Fire the `openapi.format.after` event — scripts can
 *    mutate the in-memory document before serialization. The document is
 *    exposed via the rule context extension `"document"`.
 * 6. Serialize via [OpenApiSerializer] — JSON (Gson) or YAML (Jackson
 *    `YAMLMapper`) depending on the effective format.
 * 7. Wrap the document + format + pre-serialized content in
 *    [OpenApiExportMetadata] and return `ExportResult.Success`.
 *
 * ## handleResult
 *
 * Writes the pre-serialized content to the user-chosen file using
 * `background { }` for the I/O and `swing { }` for the success dialog (mirrors
 * [com.itangcent.easyapi.channel.markdown.MarkdownChannel.handleResult] and
 * [com.itangcent.easyapi.channel.hoppscotch.HoppscotchChannel.handleFileExport]).
 *
 * Default file name is `openapi.json` or `openapi.yaml` based on the format.
 * Throws [CancellationException] when the user cancels the save dialog so the
 * orchestrator can propagate the cancellation.
 *
 * ## Logging
 *
 * Implements [IdeaLog]; uses `LOG.info` for entry / per-skip / completion
 * milestones and `LOG.warn` for recoverable failures. No `LOG.error` /
 * `LOG.debug` / `LOG.trace` (AGENTS.md §"Logging" → Anti-patterns).
 *
 * @see OpenApiFormatter for the conversion core (pure)
 * @see OpenApiSerializer for the JSON + YAML serializers (pure, channel-local)
 * @see OpenApiOptionsPanel for the per-export options panel
 * @see OpenApiSettingsPanel for the persistent settings tab
 * @see OpenApiEnvelope for the rule-resolved envelope data carrier
 */
class OpenApiChannel : Channel, IdeaLog {

    override val id: String = "openapi"
    override val displayName: String = "OpenAPI (Beta)"
    override val supportsGrpc: Boolean = false
    override val exposeAsAction: Boolean = true
    override val actionText: String = "Export to OpenAPI"
    override val enabledByDefault: Boolean = false
    override val beta: Boolean = true
    override val settingsType: KClass<out Settings> = OpenApiSettings::class

    override fun createOptionsPanel(project: Project): ChannelOptionsPanel? =
        OpenApiOptionsPanel(project)

    override fun createSettingsPanel(project: Project): SettingsPanel<*>? =
        OpenApiSettingsPanel(project)

    override fun configFiles(): List<String> = emptyList()

    override fun ruleKeys(): List<RuleKey<*>> = RuleKey.collectFrom(OpenApiRuleKeys)

    override suspend fun export(context: ExportContext): ExportResult {
        LOG.info("OpenApiChannel.export: endpoints=${context.endpointsToExport.size}")
        val project = context.project

        // 1. Filter — keep HTTP only, log gRPC skips at info.
        val (httpEndpoints, grpcEndpoints) = context.endpointsToExport.partition { it.isHttp }
        grpcEndpoints.forEach { ep ->
            LOG.info(
                "Skipping gRPC endpoint (OpenAPI does not represent gRPC): " +
                        (ep.name ?: ep.sourceMethod?.name ?: "<unknown>")
            )
        }
        if (httpEndpoints.isEmpty()) {
            return ExportResult.Error("No HTTP endpoints to export")
        }

        // 2. Resolve the effective output format.
        //    Precedence: options-panel > settings > built-in default (ALWAYS_ASK).
        //    When ALWAYS_ASK is selected, prompt the user on EDT — throws
        //    CancellationException on cancel.
        val settings = project.settings<OpenApiSettings>()
        val typed = (context.channelConfig as? OpenApiConfig) ?: OpenApiConfig()
        val typedFormat = typed.outputFormat
        val effectiveFormat = when (typedFormat) {
            OpenApiOutputFormat.ALWAYS_ASK -> {
                // If the typed config is ALWAYS_ASK, defer to the settings; if
                // settings is ALSO ALWAYS_ASK, prompt the user.
                val settingsFormat = OpenApiConfig.parseOutputFormat(settings.outputFormat)
                when (settingsFormat) {
                    OpenApiOutputFormat.ALWAYS_ASK -> promptFormat(project)
                    else -> settingsFormat
                }
            }
            else -> typedFormat
        }

        // 3. Resolve envelope metadata from rules only.
        //    No settings/options-panel fallback for these fields.
        val envelope = resolveRuleBasedEnvelope(project, project.name)

        // 4. Build the document (pure — no rule engine, no I/O).
        val formatter = OpenApiFormatter(project)
        val document = formatter.format(httpEndpoints, envelope)

        // 5. Fire openapi.format.after event.
        //    Scripts can mutate the in-memory document before serialization.
        fireFormatAfterEvent(project, httpEndpoints, document)

        // 6. Serialize. ALWAYS_ASK is already resolved above.
        val content = when (effectiveFormat) {
            OpenApiOutputFormat.JSON -> OpenApiSerializer.toJson(document)
            OpenApiOutputFormat.YAML -> OpenApiSerializer.toYaml(document)
            OpenApiOutputFormat.ALWAYS_ASK -> error("unreachable — ALWAYS_ASK resolved at step 2")
        }

        return ExportResult.Success(
            count = httpEndpoints.size,
            target = "OpenAPI",
            metadata = OpenApiExportMetadata(document, effectiveFormat, content),
        )
    }

    // ─── ALWAYS_ASK prompt ────────────────────────────────────────────

    /**
     * Shows a choose dialog on EDT prompting the user to pick JSON / YAML.
     * Throws [CancellationException] on cancel.
     * Mirrors `CurlExportResolver.resolveRenderMode` `ALWAYS_ASK` pattern.
     *
     * Uses [Messages.showDialog]: it returns the pressed button index
     * (`0..options.size - 1`) or `-1` on cancel — the same contract the
     * deprecated `Messages.showChooseDialog` had.
     *
     * Do not route this through `MessagesService.getInstance()` even though it
     * is the documented replacement for `showChooseDialog`: the whole service is
     * annotated `@ApiStatus.Internal`. Using it trades one deprecated-API
     * warning for five internal-API ones, and the Plugin Verifier fails the
     * build on internal API usages by default.
     *
     * @requires EDT (called via [swing]); the caller is responsible for
     *  wrapping with `swing { ... }`.
     */
    private suspend fun promptFormat(project: Project): OpenApiOutputFormat = swing {
        val choice = Messages.showDialog(
            project,
            "Select output format for OpenAPI export:",
            "OpenAPI Export - Format",
            arrayOf("JSON", "YAML"),
            0,
            Messages.getQuestionIcon(),
        )
        when (choice) {
            -1 -> throw CancellationException("User cancelled format selection")
            0 -> OpenApiOutputFormat.JSON
            else -> OpenApiOutputFormat.YAML
        }
    }

    // ─── Rule evaluation (envelope, not config) ─────────────────────

    /**
     * Evaluates the document-metadata rule keys ONCE per export operation
     * (without an element context) and returns the resolved envelope.
     *
     * Document-level rule keys
     * (`openapi.info.title`, `openapi.info.version`, `openapi.info.description`,
     * `openapi.server.url`, `openapi.host`) describe the WHOLE document —
     * there is only one `info` block and one `servers` array per OpenAPI
     * document. Evaluating them per `sourceClass` (the previous behaviour)
     * was incorrect because:
     *
     * 1. The extension config files (`swagger3-openapi.config`,
     *    `swagger-openapi.config`, `springfox-openapi.config`) now use
     *    `helper.findClassesByAnnotation(...)` / `helper.findMethodsByAnnotation(...)`
     *    to locate document-level annotations (`@OpenAPIDefinition`,
     *    `@SwaggerDefinition`, Springfox `@Bean Docket`) GLOBALLY — they do
     *    NOT reference `it` (the current element) at all. Evaluating them
     *    once is both correct and sufficient.
     * 2. Per-class evaluation produced redundant `helper.findClassesByAnnotation`
     *    calls (one per controller class) for the same global result.
     *
     * The no-arg `ruleEngine.evaluate(key)` overload uses
     * `RuleContext.withoutElement(project)` — the scripts still have access to
     * the `helper` binding (which resolves the project from the rule context)
     * and to `logger`/`LOG`. The `it` binding is an empty `ScriptItContext`
     * with no PSI element; scripts that relied on `it.annMap(...)` (the old
     * swagger3/swagger config files) would return null — but those files were
     * rewritten to use `helper.findClassesByAnnotation(...)` instead,
     * so this is no longer a concern.
     *
     * - `openapi.host` is evaluated first (legacy alias, parity with
     *   `hopp.host` / `postman.host`), then `openapi.server.url` as the
     *   OAS-native fallback.
     * - Built-in defaults: `infoTitle = projectName ?: "API"`,
     *   `infoVersion = "1.0.0"`, `infoDescription = null`, `serverUrl = null`.
     *
     * @requires ReadAction context (rule engine may access PSI via `helper.*`)
     */
    private suspend fun resolveRuleBasedEnvelope(
        project: Project,
        projectName: String?,
    ): OpenApiEnvelope {
        val ruleEngine = RuleEngine.getInstance(project)

        // Document-level keys: evaluated once (no element context).
        // The extension config scripts use `helper.findClassesByAnnotation(...)`
        // / `helper.findMethodsByAnnotation(...)` which resolve the project
        // from the rule context — they do not need an `it` element.
        var infoTitle: String? = ruleEngine.evaluate(OpenApiRuleKeys.OPENAPI_INFO_TITLE)
            ?.takeIf { it.isNotBlank() }
        if (infoTitle != null) LOG.info("openapi.info.title resolved by rule")

        var infoVersion: String? = ruleEngine.evaluate(OpenApiRuleKeys.OPENAPI_INFO_VERSION)
            ?.takeIf { it.isNotBlank() }
        if (infoVersion != null) LOG.info("openapi.info.version resolved by rule")

        var infoDescription: String? = ruleEngine.evaluate(OpenApiRuleKeys.OPENAPI_INFO_DESCRIPTION)
            ?.takeIf { it.isNotBlank() }
        if (infoDescription != null) LOG.info("openapi.info.description resolved by rule")

        // openapi.host is evaluated first (parity with hopp.host / postman.host),
        // then openapi.server.url as the OAS-native fallback.
        var serverUrl: String? = ruleEngine.evaluate(OpenApiRuleKeys.OPENAPI_HOST)
            ?.takeIf { it.isNotBlank() }
            ?: ruleEngine.evaluate(OpenApiRuleKeys.OPENAPI_SERVER_URL)
                ?.takeIf { it.isNotBlank() }
        if (serverUrl != null) LOG.info("openapi.server.url resolved by rule")

        return OpenApiEnvelope(
            infoTitle = infoTitle ?: projectName ?: "API",
            infoVersion = infoVersion ?: "1.0.0",
            infoDescription = infoDescription,
            serverUrl = serverUrl,
        )
    }

    /**
     * Fires the `openapi.format.after` event against the first available
     * `ApiEndpoint.sourceMethod`. The built [OpenApiDocument] is exposed to
     * scripts via the rule context extension `"document"` — scripts can
     * mutate it in place (e.g. inject vendor extensions, sort tags, scrub
     * fields) before serialization.
     *
     * No-op when no `sourceMethod` is available or when no rules are defined
     * for the key (the rule engine returns early in that case).
     */
    private suspend fun fireFormatAfterEvent(
        project: Project,
        httpEndpoints: List<ApiEndpoint>,
        document: OpenApiDocument,
    ) {
        val firstMethod = httpEndpoints.firstNotNullOfOrNull { it.sourceMethod } ?: return
        val ruleEngine = RuleEngine.getInstance(project)
        ruleEngine.evaluate(OpenApiRuleKeys.OPENAPI_FORMAT_AFTER, firstMethod) { ctx ->
            ctx.setExt("document", document)
        }
    }

    override suspend fun handleResult(
        project: Project,
        result: ExportResult.Success,
        config: ChannelConfig,
    ): Boolean {
        val metadata = result.metadata as? OpenApiExportMetadata ?: return false

        val defaultFileName = defaultFileName(metadata.outputFormat)
        val targetFile = resolveTargetFile(project, config, defaultFileName)
            ?: throw CancellationException("User cancelled file selection")

        background {
            targetFile.writeText(metadata.content)
        }
        LOG.info("OpenAPI exported to ${targetFile.absolutePath}")

        swing {
            Messages.showInfoMessage(
                project,
                "Successfully exported ${result.count} endpoints to ${targetFile.absolutePath}",
                "Export API",
            )
        }
        return true
    }

    /**
     * Default output file name for the given [format].
     */
    private fun defaultFileName(format: OpenApiOutputFormat): String = when (format) {
        OpenApiOutputFormat.JSON -> "openapi.json"
        OpenApiOutputFormat.YAML -> "openapi.yaml"
        OpenApiOutputFormat.ALWAYS_ASK -> error("unreachable — ALWAYS_ASK resolved at export step 2")
    }

    /**
     * Resolves the target output file from the channel config.
     *
     * Accepts a [ChannelConfig.FileConfig] (SPI base) for the
     * [outputDir][ChannelConfig.FileConfig.outputDir] /
     * [fileName][ChannelConfig.FileConfig.fileName] fields. When `fileName` has
     * no extension, the format-specific extension (`.json` / `.yaml`) is
     * appended; when `fileName` already carries an extension, it is used as-is.
     *
     * Returns `null` to signal "user must be prompted" — the caller then
     * invokes [selectTargetFile] which throws [CancellationException] on
     * cancel.
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
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val name = when {
                fileName.isNullOrBlank() -> defaultFileName
                fileName.contains('.') -> fileName
                else -> "$fileName.${defaultFileName.substringAfterLast('.')}"
            }
            return File(dir, name)
        }
        return selectTargetFile(project, defaultFileName)
    }

    private suspend fun selectTargetFile(project: Project, defaultFileName: String): File? {
        return swing {
            val descriptor = FileSaverDescriptor(
                "Save OpenAPI Document",
                "Choose where to save the OpenAPI document",
            )
            val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            val wrapper: VirtualFileWrapper? = saver.save(null as VirtualFile?, defaultFileName)
            wrapper?.file
        }
    }
}
