package com.itangcent.easyapi.exporter.channel.markdown.template

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.SourceValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Pins the contract of [MarkdownTemplateResolver].
 *
 * Covers the precedence chain:
 *
 * ```
 * 1. TemplateConfig.templateInline             (UI panel, non-blank)
 * 2. TemplateConfig.templatePath               (UI panel, local file exists)
 * 3. TemplateConfig.templateUrl                (UI panel, http(s) URL, fetch succeeds)
 * 4. ConfigReader "markdown.template"          (auto-detect: URL → fetch, file → read, else inline)
 * 5. <projectBasePath>/.easyapi/markdown.tpl   (convention-based project file)
 * 6. ConfigReader "markdown.template.language" (bundled locale template)
 * 7. bundled default.md.tpl
 * ```
 *
 * ## Failure behavior 
 *
 * - **Blank/unset** → default, **no warning** .
 * - **Missing local file** (tier 2/5) → fall through + `warn` (captured in
 *   [ResolveResult.warnings]) .
 * - **Remote fetch failure** (tier 3/4) → fall through + `warn` .
 * - **Parse/exec failure** of the resolved template → [MarkdownTemplateRenderer.renderWithFallback]
 *   re-renders with the default template and appends the distinguishable log line .
 * - **Missing var/helper** → empty + no fallback .
 *
 * The resolver itself is **pure**: it returns warnings as data ([ResolveWarning]) rather
 * than calling `IdeaConsole`/`NotificationUtils` directly.
 */
class MarkdownTemplateResolverTest {

    // ────────────────────────────── Fakes ──────────────────────────────

    private class FakeConfigReader(private val entries: Map<String, String>) : ConfigReader {
        override fun getFirst(key: String): String? = entries[key]
        override fun getAll(key: String): List<String> = listOfNotNull(entries[key])
        override fun sourcesForKey(key: String): List<SourceValue> = emptyList()
        override suspend fun reload() {}
        override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {
            entries.forEach { (k, v) -> if (keyFilter(k)) action(k, v) }
        }
    }

    private fun fakeReader(vararg pairs: Pair<String, String>): FakeConfigReader =
        FakeConfigReader(mapOf(*pairs))

    /** A file reader backed by an in-memory path→content map. Missing paths return null. */
    private fun fileMap(files: Map<String, String>): (String) -> String? = { files[it] }

    private fun noFiles(): (String) -> String? = { null }

    /**
     * Non-suspend wrapper around the suspend [MarkdownTemplateResolver.resolve].
     */
    private fun resolveSync(
        config: TemplateConfig? = null,
        configReader: ConfigReader? = null,
        projectBasePath: String? = null,
        fileReader: (String) -> String? = { null },
        urlFetcher: suspend (String) -> FetchResult? = { null },
    ): ResolveResult = runBlocking {
        MarkdownTemplateResolver.resolve(
            config = config,
            configReader = configReader,
            projectBasePath = projectBasePath,
            fileReader = fileReader,
            urlFetcher = urlFetcher,
        )
    }

    /** A urlFetcher that always returns [FetchResult.Ok] with the given text. */
    private fun okFetcher(text: String): suspend (String) -> FetchResult? = { FetchResult.Ok(text) }

    /** A urlFetcher that always returns [FetchResult.Failed] with the given reason. */
    private fun failedFetcher(reason: String, t: Throwable? = null): suspend (String) -> FetchResult? =
        { FetchResult.Failed(reason, t) }

    // ──────────────────────────── Test fixtures ────────────────────────

    private val defaultTemplate: String = DefaultMarkdownTemplate.get()

    private val ctx: RenderContext = RenderContext(
        clock = Clock.fixed(Instant.parse("2026-03-15T10:30:45Z"), ZoneId.of("UTC")),
        zone = ZoneId.of("UTC"),
        username = "testuser",
        projectName = "test-project",
        pluginVersion = "1.0.0-test",
    )

    private fun emptyModel(): TemplateModel = TemplateModel(
        moduleName = "Test",
        groups = emptyList(),
        endpointCount = 0,
    )

    // ════════════════════ Precedence chain ════════════════════

    // ── Tier 1: UI inline wins over everything ──

    @Test
    fun testUiInlineWinsOverUiPathAndConfig() {
        val config = TemplateConfig(
            templateInline = "UI_INLINE_CONTENT",
            templatePath = "/ui/template.md",
        )
        val reader = fakeReader("markdown.template" to "/config/template.md")
        val files = fileMap(mapOf(
            "/ui/template.md" to "UI_FILE_CONTENT",
            "/config/template.md" to "CONFIG_FILE_CONTENT",
        ))

        val result = resolveSync(config, reader, fileReader = files)

        assertEquals("UI_INLINE_CONTENT", result.templateText)
        assertEquals("ui-inline", result.source)
        assertTrue("No warnings when UI inline is valid", result.warnings.isEmpty())
    }

    // ── Tier 2: UI path wins over config ──

    @Test
    fun testUiPathWinsOverConfig() {
        val config = TemplateConfig(templatePath = "/ui/template.md")
        val reader = fakeReader("markdown.template" to "/config/template.md")
        val files = fileMap(mapOf(
            "/ui/template.md" to "UI_FILE_CONTENT",
            "/config/template.md" to "CONFIG_FILE_CONTENT",
        ))

        val result = resolveSync(config, reader, fileReader = files)

        assertEquals("UI_FILE_CONTENT", result.templateText)
        assertEquals("ui-path", result.source)
        assertTrue("No warnings when UI path file exists", result.warnings.isEmpty())
    }

    // ── Tier 3: UI URL wins over config ──

    @Test
    fun testUiUrlWinsOverConfig() {
        val config = TemplateConfig(templateUrl = "https://example.com/t.md")
        val reader = fakeReader("markdown.template" to "/config/template.md")
        val files = fileMap(mapOf("/config/template.md" to "CONFIG_FILE_CONTENT"))

        val result = resolveSync(config, reader, fileReader = files, urlFetcher = okFetcher("REMOTE"))

        assertEquals("REMOTE", result.templateText)
        assertEquals("ui-url", result.source)
    }

    // ── Tier 4: config `markdown.template` (auto-detect file vs URL vs inline) ──

    @Test
    fun testConfigWithFilePathReadsFile() {
        val reader = fakeReader("markdown.template" to "/config/template.md")
        val files = fileMap(mapOf("/config/template.md" to "CONFIG_FILE_CONTENT"))

        val result = resolveSync(config = null, reader, fileReader = files)

        assertEquals("CONFIG_FILE_CONTENT", result.templateText)
        assertEquals("config", result.source)
        assertTrue("No warnings when config file exists", result.warnings.isEmpty())
    }

    @Test
    fun testConfigWithUrlFetchesRemote() {
        val reader = fakeReader("markdown.template" to "https://example.com/t.md")

        val result = resolveSync(
            config = null, reader, fileReader = noFiles(),
            urlFetcher = okFetcher("REMOTE_TEMPLATE"),
        )

        assertEquals("REMOTE_TEMPLATE", result.templateText)
        assertEquals("config", result.source)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun testConfigWithInlineContentFallsBackToInline() {
        // A non-URL value that isn't an existing file is treated as inline template content.
        val reader = fakeReader("markdown.template" to "# Custom\n{{moduleName}}")

        val result = resolveSync(config = null, reader, fileReader = noFiles())

        assertEquals("# Custom\n{{moduleName}}", result.templateText)
        assertEquals("config", result.source)
        assertTrue("Inline fallback should not produce a warning", result.warnings.isEmpty())
    }

    @Test
    fun testConfigUrlFetchFailureFallsBackWithWarning() {
        val reader = fakeReader("markdown.template" to "https://example.com/t.md")

        val result = resolveSync(
            config = null, reader, fileReader = noFiles(),
            urlFetcher = failedFetcher("HTTP 503"),
        )

        assertEquals(defaultTemplate, result.templateText)
        assertEquals("default", result.source)
        assertEquals(1, result.warnings.size)
        assertEquals("config", result.warnings[0].tier)
        assertTrue(
            "Warning should mention the failure: ${result.warnings[0].message}",
            result.warnings[0].message.contains("503"),
        )
    }

    @Test
    fun testConfigUrlFetchFailureWithThrowablePreservesThrowable() {
        val reader = fakeReader("markdown.template" to "https://example.com/t.md")
        val ioError = java.io.IOException("connection reset")

        val result = resolveSync(
            config = null, reader, fileReader = noFiles(),
            urlFetcher = failedFetcher("transport error", ioError),
        )

        assertEquals(defaultTemplate, result.templateText)
        assertEquals("default", result.source)
        assertEquals(1, result.warnings.size)
        assertNotNull("Throwable should be preserved in the warning", result.warnings[0].throwable)
    }

    // ── Tier 5: project convention file (.easyapi/markdown.tpl) ──

    @Test
    fun testProjectConventionFileLoadedWhenNoConfigSet() {
        val projectPath = "/fake/project"
        // Mirror the resolver's path construction (File.path) so the test works on
        // Windows where path separators differ from Unix.
        val conventionFile = File(projectPath, ".easyapi/markdown.tpl").path
        val files = fileMap(mapOf(conventionFile to "PROJECT_TEMPLATE"))

        val result = resolveSync(
            config = null, configReader = null,
            projectBasePath = projectPath, fileReader = files,
        )

        assertEquals("PROJECT_TEMPLATE", result.templateText)
        assertEquals("project-file", result.source)
        assertTrue("Convention file should not produce a warning", result.warnings.isEmpty())
    }

    @Test
    fun testConfigOverridesProjectConventionFile() {
        val projectPath = "/fake/project"
        // Mirror the resolver's path construction (File.path) so the test works on
        // Windows where path separators differ from Unix.
        val conventionFile = File(projectPath, ".easyapi/markdown.tpl").path
        val reader = fakeReader("markdown.template" to "/config/template.md")
        val files = fileMap(mapOf(
            conventionFile to "PROJECT_TEMPLATE",
            "/config/template.md" to "CONFIG_FILE_CONTENT",
        ))

        val result = resolveSync(
            config = null, reader,
            projectBasePath = projectPath, fileReader = files,
        )

        assertEquals("CONFIG_FILE_CONTENT", result.templateText)
        assertEquals("config", result.source)
    }

    @Test
    fun testProjectConventionFileMissingFallsThroughSilently() {
        // No convention file exists → no warning, falls through to default.
        val result = resolveSync(
            config = null, configReader = null,
            projectBasePath = "/fake/project", fileReader = noFiles(),
        )

        assertEquals(defaultTemplate, result.templateText)
        assertEquals("default", result.source)
        assertTrue("Missing convention file should not produce a warning", result.warnings.isEmpty())
    }

    @Test
    fun testProjectConventionFileOverridesLanguage() {
        val projectPath = "/fake/project"
        // Mirror the resolver's path construction (File.path) so the test works on
        // Windows where path separators differ from Unix.
        val conventionFile = File(projectPath, ".easyapi/markdown.tpl").path
        val reader = fakeReader("markdown.template.language" to "zh-CN")
        val files = fileMap(mapOf(conventionFile to "PROJECT_TEMPLATE"))

        val result = resolveSync(
            config = null, reader,
            projectBasePath = projectPath, fileReader = files,
        )

        assertEquals("PROJECT_TEMPLATE", result.templateText)
        assertEquals("project-file", result.source)
    }

    @Test
    fun testNullProjectBasePathSkipsConventionTier() {
        val result = resolveSync(
            config = null, configReader = null,
            projectBasePath = null, fileReader = noFiles(),
        )

        assertEquals(defaultTemplate, result.templateText)
        assertEquals("default", result.source)
    }

    // ── Tier 6: language ──

    @Test
    fun testConfigLanguageKeySelectsZhCNTemplate() {
        val reader = fakeReader("markdown.template.language" to "zh-CN")

        val result = resolveSync(config = null, reader, fileReader = noFiles())

        val zhTemplate = BundledLanguageTemplates.templateFor("zh-CN")
        assertEquals(zhTemplate, result.templateText)
        assertEquals("config-language:zh-CN", result.source)
        assertTrue("Supported locale should not produce warnings", result.warnings.isEmpty())
    }

    @Test
    fun testUiLanguageConfigSelectsZhCNTemplate() {
        val config = TemplateConfig(templateLanguage = "zh-CN")

        val result = resolveSync(config, configReader = null, fileReader = noFiles())

        val zhTemplate = BundledLanguageTemplates.templateFor("zh-CN")
        assertEquals(zhTemplate, result.templateText)
        assertEquals("config-language:zh-CN", result.source)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun testUiLanguageOverridesConfigLanguage() {
        val config = TemplateConfig(templateLanguage = "zh-CN")
        val reader = fakeReader("markdown.template.language" to "fr")

        val result = resolveSync(config, reader, fileReader = noFiles())

        assertEquals(BundledLanguageTemplates.templateFor("zh-CN"), result.templateText)
        assertEquals("config-language:zh-CN", result.source)
    }

    @Test
    fun testUnsupportedLocaleFallsBackToDefaultWithWarning() {
        val reader = fakeReader("markdown.template.language" to "xx-XX")

        val result = resolveSync(config = null, reader, fileReader = noFiles())

        assertEquals(defaultTemplate, result.templateText)
        assertEquals("default", result.source)
        assertEquals(1, result.warnings.size)
        assertEquals("config-language", result.warnings[0].tier)
        assertTrue("Warning should name the unsupported locale",
            result.warnings[0].message.contains("xx-XX"))
    }

    @Test
    fun testBlankLanguageFallsThroughToDefault() {
        val reader = fakeReader("markdown.template.language" to "  ")

        val result = resolveSync(config = null, reader, fileReader = noFiles())

        assertEquals(defaultTemplate, result.templateText)
        assertEquals("default", result.source)
        assertTrue("Blank language should not produce a warning ", result.warnings.isEmpty())
    }

    @Test
    fun testConfigOverridesLanguage() {
        val reader = fakeReader(
            "markdown.template" to "/config/template.md",
            "markdown.template.language" to "zh-CN",
        )
        val files = fileMap(mapOf("/config/template.md" to "CONFIG_FILE_CONTENT"))

        val result = resolveSync(config = null, reader, fileReader = files)

        assertEquals("CONFIG_FILE_CONTENT", result.templateText)
        assertEquals("config", result.source)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun testUiInlineOverridesLanguage() {
        val config = TemplateConfig(
            templateInline = "UI_INLINE",
            templateLanguage = "zh-CN",
        )

        val result = resolveSync(config, configReader = null, fileReader = noFiles())

        assertEquals("UI_INLINE", result.templateText)
        assertEquals("ui-inline", result.source)
    }

    // ── Tier 7: default when nothing configured ──

    @Test
    fun testDefaultWhenNothingConfigured() {
        val result = resolveSync(
            config = null, configReader = null,
            fileReader = noFiles(),
        )

        assertEquals(defaultTemplate, result.templateText)
        assertEquals("default", result.source)
        assertTrue("No warnings when falling back to default", result.warnings.isEmpty())
    }

    // ════════════════════ Blank / unset  ════════════════════

    @Test
    fun testBlankUiInlineFallsThroughToUiPath() {
        val config = TemplateConfig(
            templateInline = "   ",  // whitespace-only → treated as blank
            templatePath = "/ui/template.md",
        )
        val files = fileMap(mapOf("/ui/template.md" to "UI_FILE_CONTENT"))

        val result = resolveSync(config, configReader = null, fileReader = files)

        assertEquals("UI_FILE_CONTENT", result.templateText)
        assertEquals("ui-path", result.source)
        assertTrue("Blank inline should not produce a warning", result.warnings.isEmpty())
    }

    @Test
    fun testBlankUiInlineAndMissingUiPathFallsThroughToConfig() {
        val config = TemplateConfig(
            templateInline = "",
            templatePath = "/ui/missing.md",
        )
        val reader = fakeReader("markdown.template" to "CONFIG_INLINE")
        val files = noFiles()  // UI path file doesn't exist

        val result = resolveSync(config, reader, fileReader = files)

        // CONFIG_INLINE is not a URL and not an existing file → treated as inline content
        assertEquals("CONFIG_INLINE", result.templateText)
        assertEquals("config", result.source)
        assertEquals(1, result.warnings.size)
        assertEquals("ui-path", result.warnings[0].tier)
    }

    @Test
    fun testAllBlankFallsToDefaultWithNoWarnings() {
        val config = TemplateConfig(templateInline = "  ", templatePath = "  ")
        val reader = fakeReader("markdown.template" to "")

        val result = resolveSync(config, reader, fileReader = noFiles())

        assertEquals(defaultTemplate, result.templateText)
        assertEquals("default", result.source)
        assertTrue("All-blank should produce no warnings ", result.warnings.isEmpty())
    }

    // ════════════════════ Missing file → fallback + warn  ════════════════════

    @Test
    fun testMissingUiPathFileFallsBackWithWarning() {
        val config = TemplateConfig(templatePath = "/nonexistent/template.md")
        val result = resolveSync(config, configReader = null, fileReader = noFiles())

        assertEquals(defaultTemplate, result.templateText)
        assertEquals("default", result.source)
        assertEquals(1, result.warnings.size)
        val warning = result.warnings[0]
        assertEquals("ui-path", warning.tier)
        assertNotNull("Warning message should not be null", warning.message)
        assertTrue("Warning should mention the missing path",
            warning.message.contains("/nonexistent/template.md"))
    }

    @Test
    fun testMissingUiPathFallsThroughToConfig() {
        val config = TemplateConfig(templatePath = "/nonexistent/ui.md")
        val reader = fakeReader("markdown.template" to "CONFIG_INLINE")

        val result = resolveSync(config, reader, fileReader = noFiles())

        assertEquals("CONFIG_INLINE", result.templateText)
        assertEquals("config", result.source)
        assertEquals(1, result.warnings.size)
        assertEquals("ui-path", result.warnings[0].tier)
    }

    // ════════════════════ Parse/exec failure → fallback  ════════════════════

    @Test
    fun testParseFailureFallsBackWithDistinguishableLine() {
        val brokenTemplate = "Hello {{#if name}}no closing tag"

        val result = resolveSync(
            config = TemplateConfig(templateInline = brokenTemplate),
            configReader = null, fileReader = noFiles(),
        )

        assertEquals(brokenTemplate, result.templateText)
        assertEquals("ui-inline", result.source)

        val rendered = MarkdownTemplateRenderer.renderWithFallback(result.templateText, emptyModel(), ctx)

        assertTrue("Fallback should contain default template output",
            rendered.contains("Test") || rendered.isNotEmpty())
        assertFalse("Fallback should not contain the broken template's literal text",
            rendered.startsWith("Hello {{#if name}}"))
        assertTrue("Should have distinguishable fallback line ",
            rendered.contains("Rendered with DEFAULT template (user template failed:"))
    }

    @Test
    fun testExecFailureFallsBackWithDistinguishableLine() {
        val execBrokenTemplate = "{{#each moduleName as item}}{{item}}{{/each}}"

        val result = resolveSync(
            config = TemplateConfig(templateInline = execBrokenTemplate),
            configReader = null, fileReader = noFiles(),
        )

        assertEquals(execBrokenTemplate, result.templateText)

        val rendered = MarkdownTemplateRenderer.renderWithFallback(result.templateText, emptyModel(), ctx)

        assertTrue("Exec failure should have distinguishable fallback line",
            rendered.contains("Rendered with DEFAULT template (user template failed:"))
    }

    // ════════════════════ Missing var → empty, no fallback  ════════════════════

    @Test
    fun testMissingVarResolvesToEmptyNoFallback() {
        val templateWithMissingVar = "Hello {{nonexistent.variable}}!"

        val result = resolveSync(
            config = TemplateConfig(templateInline = templateWithMissingVar),
            configReader = null, fileReader = noFiles(),
        )

        assertEquals(templateWithMissingVar, result.templateText)

        val rendered = MarkdownTemplateRenderer.render(result.templateText, emptyModel(), ctx)

        assertEquals("Hello !", rendered)
        assertFalse("Missing var should NOT trigger fallback line",
            rendered.contains("Rendered with DEFAULT template"))
    }

    @Test
    fun testUnknownHelperResolvesToEmptyNoFallback() {
        val templateWithUnknownHelper = "Result: {{unknownHelper name}}"

        val result = resolveSync(
            config = TemplateConfig(templateInline = templateWithUnknownHelper),
            configReader = null, fileReader = noFiles(),
        )

        val rendered = MarkdownTemplateRenderer.render(result.templateText, emptyModel(), ctx)

        assertEquals("Result: ", rendered)
        assertFalse("Unknown helper should NOT trigger fallback line",
            rendered.contains("Rendered with DEFAULT template"))
    }

    // ════════════════════ Distinguishability  ════════════════════

    @Test
    fun testSuccessfulRenderHasNoFallbackLine() {
        val validTemplate = "# Custom Template\nModule: {{moduleName}}"

        val result = resolveSync(
            config = TemplateConfig(templateInline = validTemplate),
            configReader = null, fileReader = noFiles(),
        )

        assertEquals(validTemplate, result.templateText)
        assertEquals("ui-inline", result.source)

        val rendered = MarkdownTemplateRenderer.render(result.templateText, emptyModel(), ctx)

        assertEquals("# Custom Template\nModule: Test", rendered)
        assertFalse("Successful render should NOT have fallback line",
            rendered.contains("Rendered with DEFAULT template"))
    }

    // ════════════════════ Null config reader (DefaultMarkdownFormatter path) ════════════════════

    @Test
    fun testNullConfigReaderWithNullConfigReturnsDefault() {
        val result = resolveSync(
            config = null, configReader = null, fileReader = noFiles(),
        )

        assertEquals(defaultTemplate, result.templateText)
        assertEquals("default", result.source)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun testNullConfigReaderWithUiInlineReturnsUiInline() {
        val result = resolveSync(
            config = TemplateConfig(templateInline = "ONLY_UI"),
            configReader = null, fileReader = noFiles(),
        )

        assertEquals("ONLY_UI", result.templateText)
        assertEquals("ui-inline", result.source)
    }

    // ════════════════════ URL tiers ════════════════════

    @Test
    fun testUiUrlFetchReturnsOk() {
        val config = TemplateConfig(templateUrl = "https://example.com/t.md")

        val result = resolveSync(
            config = config, configReader = null, fileReader = noFiles(),
            urlFetcher = okFetcher("REMOTE_TEMPLATE"),
        )

        assertEquals("REMOTE_TEMPLATE", result.templateText)
        assertEquals("ui-url", result.source)
        assertTrue("Successful fetch should not produce warnings", result.warnings.isEmpty())
    }

    @Test
    fun testUiUrlOverridesConfigUrl() {
        val config = TemplateConfig(templateUrl = "https://ui.example.com/t.md")
        val reader = fakeReader("markdown.template" to "https://config.example.com/t.md")

        val result = resolveSync(
            config = config, reader, fileReader = noFiles(),
            urlFetcher = { url ->
                if (url == "https://ui.example.com/t.md") {
                    FetchResult.Ok("UI_REMOTE")
                } else {
                    FetchResult.Ok("CONFIG_REMOTE")
                }
            },
        )

        assertEquals("UI_REMOTE", result.templateText)
        assertEquals("ui-url", result.source)
    }

    @Test
    fun testUrlFetchFailureFallsBackWithWarning() {
        val reader = fakeReader("markdown.template" to "https://example.com/t.md")

        val result = resolveSync(
            config = null, reader, fileReader = noFiles(),
            urlFetcher = failedFetcher("HTTP 503"),
        )

        assertEquals(defaultTemplate, result.templateText)
        assertEquals("default", result.source)
        assertEquals(1, result.warnings.size)
        assertEquals("config", result.warnings[0].tier)
        assertTrue(
            "Warning should mention the failure: ${result.warnings[0].message}",
            result.warnings[0].message.contains("503"),
        )
    }

    @Test
    fun testUrlOverridesLanguage() {
        val reader = fakeReader(
            "markdown.template" to "https://example.com/t.md",
            "markdown.template.language" to "zh-CN",
        )

        val result = resolveSync(
            config = null, reader, fileReader = noFiles(),
            urlFetcher = okFetcher("REMOTE"),
        )

        assertEquals("REMOTE", result.templateText)
        assertEquals("config", result.source)
    }

    @Test
    fun testBlankUrlFallsThroughSilently() {
        val config = TemplateConfig(templateUrl = "   ")

        val result = resolveSync(config, configReader = null, fileReader = noFiles())

        assertEquals(defaultTemplate, result.templateText)
        assertEquals("default", result.source)
        assertTrue("Blank URL should not produce a warning", result.warnings.isEmpty())
    }

    @Test
    fun testUiUrlFailsThenConfigInlineSucceeds() {
        // If the UI URL fetch fails, the resolver should record a warning and continue down
        // the chain to the config tier.
        val config = TemplateConfig(templateUrl = "https://ui.example.com/t.md")
        val reader = fakeReader("markdown.template" to "CONFIG_INLINE")

        val result = resolveSync(
            config = config, reader, fileReader = noFiles(),
            urlFetcher = failedFetcher("HTTP 404"),
        )

        // CONFIG_INLINE is not a URL and not an existing file → treated as inline content
        assertEquals("CONFIG_INLINE", result.templateText)
        assertEquals("config", result.source)
        assertEquals(1, result.warnings.size)
        assertEquals("ui-url", result.warnings[0].tier)
    }

    @Test
    fun testUiUrlOverridesConfigInline() {
        val config = TemplateConfig(templateUrl = "https://example.com/t.md")
        val reader = fakeReader("markdown.template" to "CONFIG_INLINE")

        val result = resolveSync(
            config = config, reader, fileReader = noFiles(),
            urlFetcher = okFetcher("REMOTE"),
        )

        assertEquals("REMOTE", result.templateText)
        assertEquals("ui-url", result.source)
    }

    // ════════════════════ Language tier ════════════════════

    @Test
    fun testEnLanguageFallsThroughToDefault() {
        val reader = fakeReader("markdown.template.language" to "en")

        val result = resolveSync(config = null, reader, fileReader = noFiles())

        assertEquals(defaultTemplate, result.templateText)
        assertEquals("default", result.source)
        assertEquals(1, result.warnings.size)
        assertEquals("config-language", result.warnings[0].tier)
    }
}
