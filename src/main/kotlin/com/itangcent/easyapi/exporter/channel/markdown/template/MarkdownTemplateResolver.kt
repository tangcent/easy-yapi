package com.itangcent.easyapi.exporter.channel.markdown.template

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.logging.IdeaLog
import java.io.File

/**
 * Pure-data configuration for the template resolution tiers sourced from the UI panel
 * (`MarkdownOptionsPanel` → `MarkdownConfig`).
 *
 * All fields are nullable: `null` means "not set", blank (`""` / `"   "`) means "set but empty"
 * and is treated as unset by the resolver .
 *
 * @property templateInline Inline template content from the UI panel (highest precedence).
 * @property templatePath Path to a local template file from the UI panel.
 * @property templateUrl Remote URL (http(s)) from the UI panel.
 * @property templateLanguage BCP-47 locale tag for a bundled language template.
 */
data class TemplateConfig(
    val templateInline: String? = null,
    val templatePath: String? = null,
    val templateUrl: String? = null,
    val templateLanguage: String? = null,
)

/**
 * A per-tier resolution failure (e.g. a missing file, a failed remote fetch). Collected in
 * [ResolveResult.warnings] so the caller (`MarkdownChannel`) can translate them into
 * `IdeaConsole.warn` + `NotificationUtils.notifyWarning` calls .
 *
 * The resolver itself is pure — it does not call `IdeaConsole` or `NotificationUtils` directly,
 * so it is testable as pure JUnit.
 *
 * @property tier The precedence tier that failed: `"ui-path"`, `"config"`, `"project-file"`,
 *   etc.
 * @property message Human-readable description of the failure (includes the path/key/url).
 * @property throwable Optional throwable for logging (e.g. `IOException` from a file read or
 *   transport error from a remote fetch).
 */
data class ResolveWarning(
    val tier: String,
    val message: String,
    val throwable: Throwable? = null,
)

/**
 * The outcome of [MarkdownTemplateResolver.resolve].
 *
 * @property templateText The resolved template text. Never empty — the bundled default template
 *   is the floor (tier 7), so this is always a usable template string.
 * @property source A label identifying which tier produced the template:
 *   `"ui-inline"` | `"ui-path"` | `"ui-url"` | `"config"` | `"project-file"` |
 *   `"config-language:<locale>"` | `"default"`.
 * @property warnings Per-tier failures encountered while walking the precedence chain.
 *   Empty when the first applicable tier succeeded without error .
 */
data class ResolveResult(
    val templateText: String,
    val source: String,
    val warnings: List<ResolveWarning> = emptyList(),
)

/**
 * Resolves which template text to use for a Markdown export by walking the precedence chain:
 *
 * ```
 * 1. TemplateConfig.templateInline             (UI panel, non-blank)
 * 2. TemplateConfig.templatePath               (UI panel, local file exists)
 * 3. TemplateConfig.templateUrl                (UI panel, http(s) URL, fetch succeeds)
 * 4. ConfigReader "markdown.template"          (auto-detect: http(s) URL → fetch, else local file)
 * 5. <projectBasePath>/.easyapi/markdown.tpl   (convention-based project file, if it exists)
 * 6. ConfigReader "markdown.template.language"  (bundled locale template)
 * 7. bundled default.md.tpl
 * ```
 *
 * The three legacy keys (`markdown.template` inline / `.file` / `.url`) are merged into the
 * single `markdown.template` key (tier 4): the value is auto-detected as a remote URL when it
 * starts with `http://` or `https://`, otherwise it is treated as a local file path.
 *
 * Tier 5 adds convention-based auto-detection: if no explicit template is configured but a
 * `.easyapi/markdown.tpl` file exists at the project root, it is loaded automatically. This
 * lets teams share a template by simply committing the file to the repo — no config key needed.
 *
 * ## Purity
 *
 * The resolver is **pure**: it does not call `IdeaConsole`, `NotificationUtils`, or any IDE
 * service. Per-tier failures are collected as [ResolveWarning]s in the result. The caller
 * (`MarkdownChannel`) iterates [ResolveResult.warnings] and calls `IdeaConsole.warn` +
 * `NotificationUtils.notifyWarning` for each . This keeps the resolver testable
 * as pure JUnit — no `Project` needed.
 *
 * The URL tiers (3 + 4) require async I/O, so [resolve] is `suspend`. Tests wrap it in
 * `runBlocking` (or use the `resolveSync` helper in `MarkdownTemplateResolverTest`).
 *
 * ## Failure behavior
 *
 * - **Blank/unset** → skip silently (no warning) and fall through to the next tier .
 * - **Missing/unreadable local file** (tier 2/4/5) → record a [ResolveWarning] and fall through
 *   .
 * - **Remote fetch failure** (tier 3/4: network error, non-2xx, oversize, bad scheme, 3xx
 *   redirect) → [RemoteTemplateFetcher] returns [FetchResult.Failed]; resolver records a
 *   [ResolveWarning] (with throwable) and falls through . A successful fetch is
 *   cached by the fetcher; a failed one is re-attempted next export .
 * - **Unsupported locale** (tier 6) → [BundledLanguageTemplates.templateFor] returns `null`;
 *   resolver records a warning naming the locale and falls through to default .
 * - **Parse/exec failure** of the resolved template → handled at render time by
 *   [MarkdownTemplateRenderer.renderWithFallback] (not the resolver's concern).
 *
 * @see TemplateConfig
 * @see ResolveResult
 * @see RemoteTemplateFetcher
 * @see MarkdownTemplateRenderer
 */
object MarkdownTemplateResolver : IdeaLog {

    /** The convention-based relative path checked at tier 5. */
    private const val PROJECT_TEMPLATE_RELATIVE_PATH = ".easyapi/markdown.tpl"

    /**
     * Walks the precedence chain top-down and returns the first non-blank template text.
     *
     * @param config UI panel inputs (null when called from `DefaultMarkdownFormatter` which
     *   has no panel — only the default template is used).
     * @param configReader The project's [ConfigReader] for `markdown.template` /
     *   `markdown.template.language` keys (null when no config access is available — config
     *   tiers are skipped).
     * @param projectBasePath The project root path, used to resolve the convention-based
     *   `.easyapi/markdown.tpl` file (tier 5). Null skips that tier.
     * @param fileReader Reads a file path and returns its content, or `null` if the file
     *   does not exist or cannot be read. In production this wraps `java.io.File` / VFS;
     *   in tests it is an in-memory map.
     * @param urlFetcher Fetches a remote URL and returns a [FetchResult], or `null` if no
     *   fetch is performed (URL tiers are skipped). In production this wraps
     *   [RemoteTemplateFetcher.fetch] with an [HttpClient][com.itangcent.easyapi.http.HttpClient]
     *   from [HttpClientProvider][com.itangcent.easyapi.http.HttpClientProvider]; in tests it
     *   returns canned results.
     */
    suspend fun resolve(
        config: TemplateConfig? = null,
        configReader: ConfigReader? = null,
        projectBasePath: String? = null,
        fileReader: (String) -> String? = { null },
        urlFetcher: suspend (String) -> FetchResult? = { null },
    ): ResolveResult {
        val warnings = mutableListOf<ResolveWarning>()

        // ── Tier 1: UI inline ──
        config?.templateInline?.takeIfNotBlank()?.let {
            return ResolveResult(templateText = it, source = "ui-inline", warnings = warnings)
        }

        // ── Tier 2: UI path (local file) ──
        if (config != null) {
            val uiPath = config.templatePath?.takeIfNotBlank()
            if (uiPath != null) {
                val content = readTemplateFile(uiPath, fileReader)
                if (content != null) {
                    return ResolveResult(templateText = content, source = "ui-path", warnings = warnings)
                }
                warnings.add(ResolveWarning(
                    tier = "ui-path",
                    message = "Template file not found or unreadable: $uiPath",
                ))
            }
        }

        // ── Tier 3: UI URL (remote fetch) ──
        val uiUrl = config?.templateUrl?.takeIfNotBlank()
        if (uiUrl != null) {
            val fetchResult = fetchTemplate(uiUrl, urlFetcher)
            if (fetchResult is FetchResult.Ok) {
                return ResolveResult(templateText = fetchResult.text, source = "ui-url", warnings = warnings)
            }
            val (reason, throwable) = fetchFailureDetails(fetchResult)
            warnings.add(ResolveWarning(
                tier = "ui-url",
                message = "Remote template fetch failed for '$uiUrl': $reason",
                throwable = throwable,
            ))
        }

        // ── Tier 4: config `markdown.template` (auto-detect file vs URL vs inline) ──
        if (configReader != null) {
            val configValue = configReader.getFirst("markdown.template")?.takeIfNotBlank()
            if (configValue != null) {
                val resolved = resolveConfigValue(configValue, fileReader, urlFetcher, warnings)
                if (resolved != null) {
                    return ResolveResult(templateText = resolved, source = "config", warnings = warnings)
                }
                // Failure warnings (if any) were added inside resolveConfigValue.
            }
        }

        // ── Tier 5: project convention file (.easyapi/markdown.tpl) ──
        if (projectBasePath != null) {
            val projectTemplatePath = File(projectBasePath, PROJECT_TEMPLATE_RELATIVE_PATH).path
            val content = readTemplateFile(projectTemplatePath, fileReader)
            if (content != null) {
                return ResolveResult(templateText = content, source = "project-file", warnings = warnings)
            }
            // No warning — the convention file is optional; absence is the common case.
        }

        // ── Tier 6: bundled language template (`markdown.template.language`) ──
        // UI panel templateLanguage takes precedence over config key (consistent with
        // other tiers where UI > config). Unsupported locale → warn + fall through .
        val templateLanguage = config?.templateLanguage?.takeIfNotBlank()
            ?: configReader?.getFirst("markdown.template.language")?.takeIfNotBlank()
        if (templateLanguage != null) {
            val localeTemplate = BundledLanguageTemplates.templateFor(templateLanguage)
            if (localeTemplate != null) {
                // Source includes the locale so the caller's info log names the locale .
                return ResolveResult(
                    templateText = localeTemplate,
                    source = "config-language:$templateLanguage",
                    warnings = warnings,
                )
            }
            // Unsupported locale → record a warning and fall through to default .
            warnings.add(ResolveWarning(
                tier = "config-language",
                message = "No bundled template for locale '$templateLanguage'; falling back to default",
            ))
        }

        // ── Tier 7: bundled default ──
        return ResolveResult(
            templateText = DefaultMarkdownTemplate.get(),
            source = "default",
            warnings = warnings,
        )
    }

    /**
     * Resolves a `markdown.template` config value by auto-detecting whether it is a remote URL,
     * a local file path, or inline template content:
     *
     * - Values starting with `http://` or `https://` are fetched as remote URLs.
     * - Values that look like file paths (single-line with path separators, no `{{` template
     *   syntax) are read as files; if the file is missing, a warning is recorded and `null`
     *   is returned so the resolver falls through to the next tier.
     * - All other values (multi-line or containing `{{`) are treated as inline template content
     *   (backward compatibility with the legacy `markdown.template` inline semantics).
     *
     * Returns the template text on success, or `null` when a URL fetch or file read fails
     * (a [ResolveWarning] is added to [warnings] in that case).
     */
    private suspend fun resolveConfigValue(
        value: String,
        fileReader: (String) -> String?,
        urlFetcher: suspend (String) -> FetchResult?,
        warnings: MutableList<ResolveWarning>,
    ): String? {
        return if (isRemoteUrl(value)) {
            val fetchResult = fetchTemplate(value, urlFetcher)
            if (fetchResult is FetchResult.Ok) {
                fetchResult.text
            } else {
                val (reason, throwable) = fetchFailureDetails(fetchResult)
                warnings.add(ResolveWarning(
                    tier = "config",
                    message = "Remote template fetch failed for '$value': $reason",
                    throwable = throwable,
                ))
                null
            }
        } else if (looksLikeFilePath(value)) {
            // Single-line value without template syntax → treat as a file path.
            val content = readTemplateFile(value, fileReader)
            if (content != null) {
                content
            } else {
                warnings.add(ResolveWarning(
                    tier = "config",
                    message = "Template file not found or unreadable: $value",
                ))
                null
            }
        } else {
            // Multi-line or template-syntax value → inline content (backward compat).
            value
        }
    }

    /**
     * Heuristic: returns true if [value] looks like a file path rather than inline template
     * content. A file path typically contains path separators (`/` or `\`) and no `{{` template
     * variables or newlines. Inline templates usually contain newlines or `{{}}` syntax.
     */
    private fun looksLikeFilePath(value: String): Boolean {
        if (value.contains("\n") || value.contains("{{")) return false
        return value.contains("/") || value.contains("\\")
    }

    /** Returns true if [value] starts with `http://` or `https://` (case-insensitive). */
    private fun isRemoteUrl(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    /**
     * Calls the [urlFetcher] for [url] and catches any unexpected exception (the fetcher itself
     * returns [FetchResult.Failed] rather than throwing, but a misbehaving fetcher could still
     * throw — the resolver must never throw).
     */
    private suspend fun fetchTemplate(
        url: String,
        urlFetcher: suspend (String) -> FetchResult?,
    ): FetchResult? {
        return try {
            urlFetcher(url)
        } catch (t: Throwable) {
            LOG.warn("Unexpected exception from urlFetcher for $url", t)
            FetchResult.Failed("urlFetcher threw: ${t.message}", t)
        }
    }

    /** Extracts (reason, throwable) from a non-Ok [FetchResult] (or a null result). */
    private fun fetchFailureDetails(result: FetchResult?): Pair<String, Throwable?> {
        return when (result) {
            is FetchResult.Failed -> result.reason to result.throwable
            null -> "urlFetcher returned null" to null
            is FetchResult.Ok -> error("not a failure: Ok")
        }
    }

    /**
     * Reads a template file via the injected [fileReader]. Returns `null` (and logs nothing —
     * the caller records a [ResolveWarning]) if the file is missing or unreadable.
     *
     * Exceptions from [fileReader] are caught so the resolver never throws .
     */
    private fun readTemplateFile(path: String, fileReader: (String) -> String?): String? {
        return try {
            fileReader(path)
        } catch (t: Throwable) {
            LOG.warn("Failed to read template file: $path", t)
            null
        }
    }

    /** Returns the string if it is non-null and non-blank (whitespace-only → null). */
    private fun String.takeIfNotBlank(): String? = takeIf { it.isNotBlank() }
}
