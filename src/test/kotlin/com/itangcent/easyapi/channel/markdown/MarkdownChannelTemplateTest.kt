package com.itangcent.easyapi.channel.markdown

import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.channel.markdown.MarkdownExportMetadata
import com.itangcent.easyapi.channel.markdown.template.RemoteTemplateFetcher
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.testFramework.ApiFixtures
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.intellij.testFramework.registerServiceInstance
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import java.io.File
import java.net.InetSocketAddress

/**
 * End-to-end tests for [MarkdownChannel.export] covering the template resolution
 * precedence chain  and the render-failure fallback .
 *
 * Scenarios:
 * - (a) No template configured → bundled default output (tier 7).
 * - (b) `markdown.template` config key set → config tier (tier 4) produces custom output.
 * - (c) `MarkdownConfig.templatePath` from the panel → ui-path tier (tier 2) produces custom output.
 * - (d) Broken config template (exec failure) → `renderWithFallback` → default output +
 *   distinguishable fallback line observable in the content.
 * - (d-extra) Missing config template file → resolver records warning + falls through to default;
 *   export is not aborted.
 * - (e) `markdown.template.language=zh-CN` config key → zh-CN translated output (tier 6).
 * - (f) `markdown.template.language=ja` (unsupported) → default output + resolution warning.
 * - (g) `markdown.template` (config inline) overrides `markdown.template.language` .
 * - (h) Remote URL (fake server) → custom output (tier 4 config, URL auto-detected).
 * - (i) Remote fetch failure (non-2xx) → default output, export not aborted .
 * - (j) Bad scheme (`file:`) → default output, export not aborted .
 * - (k) Oversize response → default output .
 * - (l) Remote template parse/exec failure → default + distinguishable fallback line .
 *
 * The 3xx-redirect guard is exercised at the unit level in `RemoteTemplateFetcherTest` (the
 * shared `ApacheHttpClient` follows redirects by default, so the fetcher's 3xx guard cannot
 * be triggered end-to-end with a real client).
 */
class MarkdownChannelTemplateTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var channel: MarkdownChannel
    private val endpoints = ApiFixtures.createSampleEndpoints()

    override fun setUp() {
        super.setUp()
        channel = MarkdownChannel()
    }

    // ── (a) No template → default output ────────────────────────────────────

    fun testExportWithNoTemplateUsesDefault() = runBlocking {
        val context = ExportContext(
            project = project,
            endpoints = endpoints,
            channelConfig = MarkdownConfig(),
        )
        val result = channel.export(context)

        assertTrue("export should succeed", result is ExportResult.Success)
        val content = extractContent(result)
        // Default template renders `# {{{moduleName}}}` → "API Documentation" is the module name.
        assertTrue("default output should contain module name header",
            content.contains("API Documentation"))
        // No fallback should be triggered when no user template is involved.
        assertFalse("default output should not contain fallback line",
            content.contains("Rendered with DEFAULT template"))
    }

    // ── (b) markdown.template config key → custom output ─────────────────────

    fun testExportWithConfigInlineTemplateProducesCustomOutput() = runBlocking {
        registerConfigReader("markdown.template" to CUSTOM_TEMPLATE)

        val context = ExportContext(
            project = project,
            endpoints = endpoints,
            channelConfig = MarkdownConfig(),
        )
        val result = channel.export(context)

        assertTrue("export should succeed", result is ExportResult.Success)
        val content = extractContent(result)
        assertTrue("custom output should contain the custom marker",
            content.contains("CUSTOM TEMPLATE MARKER"))
        assertFalse("custom output should not contain the default module name header",
            content.startsWith("# API Documentation"))
    }

    // ── (c) MarkdownConfig.templatePath → custom output ─────────────────────

    fun testExportWithTemplatePathProducesCustomOutput() = runBlocking {
        val tempFile = File.createTempFile("custom-template", ".tpl")
        try {
            tempFile.writeText(CUSTOM_TEMPLATE)

            val context = ExportContext(
                project = project,
                endpoints = endpoints,
                channelConfig = MarkdownConfig(templatePath = tempFile.absolutePath),
            )
            val result = channel.export(context)

            assertTrue("export should succeed", result is ExportResult.Success)
            val content = extractContent(result)
            assertTrue("custom output should contain the custom marker",
                content.contains("CUSTOM TEMPLATE MARKER"))
        } finally {
            tempFile.delete()
        }
    }

    // ── (d) Broken config template (exec failure) → fallback ─────────────────

    fun testExportWithBrokenConfigTemplateFallsBackToDefault() = runBlocking {
        // `moduleName` is a String, not a List → TemplateRenderException → renderWithFallback.
        val brokenTemplate = "{{#each moduleName as item}}{{item}}{{/each}}"
        registerConfigReader("markdown.template" to brokenTemplate)

        val context = ExportContext(
            project = project,
            endpoints = endpoints,
            channelConfig = MarkdownConfig(),
        )
        val result = channel.export(context)

        assertTrue("export should not be aborted by broken template", result is ExportResult.Success)
        val content = extractContent(result)
        // The distinguishable fallback line is appended to the output .
        assertTrue("fallback output should contain the distinguishable line",
            content.contains("Rendered with DEFAULT template (user template failed:"))
        // The fallback re-renders with the default template, so default markers are present.
        assertTrue("fallback output should contain default markers",
            content.contains("API Documentation"))
    }

    // ── (d-extra) Missing config template file → resolution warning + default ─

    fun testExportWithMissingTemplateFileFallsBackToDefault() = runBlocking {
        registerConfigReader("markdown.template" to "/nonexistent/path/to/template.tpl")

        val context = ExportContext(
            project = project,
            endpoints = endpoints,
            channelConfig = MarkdownConfig(),
        )
        val result = channel.export(context)

        // Export is not aborted — resolver records a warning and falls through to default.
        assertTrue("export should not be aborted by missing file", result is ExportResult.Success)
        val content = extractContent(result)
        // Falls through to the default template (no render failure → no distinguishable line).
        assertTrue("should use default template", content.contains("API Documentation"))
        assertFalse("missing file should not trigger render fallback line",
            content.contains("Rendered with DEFAULT template"))
    }

    // ── (e) markdown.template.language=zh-CN → translated output ────────────

    fun testExportWithZhCNLanguageProducesTranslatedOutput() = runBlocking {
        registerConfigReader("markdown.template.language" to "zh-CN")

        val context = ExportContext(
            project = project,
            endpoints = endpoints,
            channelConfig = MarkdownConfig(),
        )
        val result = channel.export(context)

        assertTrue("export should succeed", result is ExportResult.Success)
        val content = extractContent(result)
        // The zh-CN template translates user-visible labels .
        assertTrue("zh-CN output should contain translated label '基本信息'",
            content.contains("基本信息"))
        assertTrue("zh-CN output should contain translated label '路径：'",
            content.contains("路径："))
        assertTrue("zh-CN output should contain translated label '请求方式：'",
            content.contains("请求方式："))
        // English labels should NOT be present when the zh-CN template is used.
        assertFalse("zh-CN output should not contain English label 'Path:'",
            content.contains("Path:"))
        assertFalse("zh-CN output should not contain English banner 'BASIC'",
            content.contains("> BASIC"))
        // No fallback should be triggered.
        assertFalse("zh-CN output should not contain fallback line",
            content.contains("Rendered with DEFAULT template"))
    }

    // ── (f) markdown.template.language=xx-XX (unsupported) → default + warning ─

    fun testExportWithUnsupportedLanguageFallsBackToDefault() = runBlocking {
        registerConfigReader("markdown.template.language" to "xx-XX")

        val context = ExportContext(
            project = project,
            endpoints = endpoints,
            channelConfig = MarkdownConfig(),
        )
        val result = channel.export(context)

        // Export is not aborted — unsupported locale falls through to default .
        assertTrue("export should not be aborted by unsupported locale",
            result is ExportResult.Success)
        val content = extractContent(result)
        // Falls through to the default template (English labels).
        assertTrue("should use default template with English labels",
            content.contains("API Documentation"))
        assertTrue("should contain English label 'Path:'",
            content.contains("Path:"))
        // Should NOT contain translated labels.
        assertFalse("should not contain zh-CN labels",
            content.contains("基本信息"))
        // No render failure → no distinguishable fallback line.
        assertFalse("unsupported locale should not trigger render fallback line",
            content.contains("Rendered with DEFAULT template"))
    }

    // ── (g) markdown.template overrides markdown.template.language  ─

    fun testExportWithConfigInlineOverridesLanguage() = runBlocking {
        registerConfigReader(
            "markdown.template" to CUSTOM_TEMPLATE,
            "markdown.template.language" to "zh-CN",
        )

        val context = ExportContext(
            project = project,
            endpoints = endpoints,
            channelConfig = MarkdownConfig(),
        )
        val result = channel.export(context)

        assertTrue("export should succeed", result is ExportResult.Success)
        val content = extractContent(result)
        // Config inline (tier 3) wins over language (tier 6).
        assertTrue("config inline should override language — custom marker present",
            content.contains("CUSTOM TEMPLATE MARKER"))
        // Should NOT contain translated labels (the zh-CN template was not used).
        assertFalse("zh-CN template should not be used when config inline is set",
            content.contains("基本信息"))
    }

    // ── (h) Remote URL (fake server) → custom output  ──────────────

    fun testExportWithRemoteUrlProducesCustomOutput() = runBlocking {
        val server = startHttpServer { exchange ->
            sendResponse(exchange, 200, CUSTOM_TEMPLATE)
        }
        try {
            RemoteTemplateFetcher.clearCacheForTesting()
            registerConfigReader("markdown.template" to serverUrl(server))

            val context = ExportContext(
                project = project,
                endpoints = endpoints,
                channelConfig = MarkdownConfig(),
            )
            val result = channel.export(context)

            assertTrue("export should succeed", result is ExportResult.Success)
            val content = extractContent(result)
            assertTrue("remote template output should contain the custom marker",
                content.contains("CUSTOM TEMPLATE MARKER"))
            assertFalse("remote template output should not contain default fallback line",
                content.contains("Rendered with DEFAULT template"))
        } finally {
            server.stop(0)
        }
    }

    // ── (i) Remote fetch failure (non-2xx) → default, not aborted  ──

    fun testExportWithRemoteFetchFailureFallsBackToDefault() = runBlocking {
        val server = startHttpServer { exchange ->
            sendResponse(exchange, 404, "Not Found")
        }
        try {
            RemoteTemplateFetcher.clearCacheForTesting()
            registerConfigReader("markdown.template" to serverUrl(server))

            val context = ExportContext(
                project = project,
                endpoints = endpoints,
                channelConfig = MarkdownConfig(),
            )
            val result = channel.export(context)

            assertTrue("export should not be aborted by remote fetch failure",
                result is ExportResult.Success)
            val content = extractContent(result)
            assertTrue("should fall back to default template", content.contains("API Documentation"))
            assertFalse("fetch failure should not trigger render fallback line",
                content.contains("Rendered with DEFAULT template"))
        } finally {
            server.stop(0)
        }
    }

    // ── (j) Bad scheme (file:) → default, not aborted  ──────────────

    fun testExportWithBadSchemeFallsBackToDefault() = runBlocking {
        RemoteTemplateFetcher.clearCacheForTesting()
        registerConfigReader("markdown.template" to "file:///etc/passwd")

        val context = ExportContext(
            project = project,
            endpoints = endpoints,
            channelConfig = MarkdownConfig(),
        )
        val result = channel.export(context)

        assertTrue("export should not be aborted by bad scheme",
            result is ExportResult.Success)
        val content = extractContent(result)
        assertTrue("should fall back to default template", content.contains("API Documentation"))
        assertFalse("bad scheme should not trigger render fallback line",
            content.contains("Rendered with DEFAULT template"))
    }

    // ── (k) Oversize response → default  ──────────────────────────

    fun testExportWithOversizeRemoteResponseFallsBackToDefault() = runBlocking {
        // Lower the cap to 100 bytes via config so we don't need to materialize a 1 MiB body.
        val oversizeBody = "x".repeat(500)
        val server = startHttpServer { exchange ->
            sendResponse(exchange, 200, oversizeBody)
        }
        try {
            RemoteTemplateFetcher.clearCacheForTesting()
            registerConfigReader(
                "markdown.template" to serverUrl(server),
                "markdown.template.url.max.bytes" to "100",
            )

            val context = ExportContext(
                project = project,
                endpoints = endpoints,
                channelConfig = MarkdownConfig(),
            )
            val result = channel.export(context)

            assertTrue("export should not be aborted by oversize response",
                result is ExportResult.Success)
            val content = extractContent(result)
            assertTrue("should fall back to default template", content.contains("API Documentation"))
            assertFalse("oversize should not trigger render fallback line",
                content.contains("Rendered with DEFAULT template"))
        } finally {
            server.stop(0)
        }
    }

    // ── (l) Remote template parse failure → default + fallback line  ─

    fun testExportWithRemoteTemplateParseFailureFallsBackToDefault() = runBlocking {
        // Unclosed {{#if}} → parse error at render time → renderWithFallback .
        val brokenRemoteTemplate = "Hello {{#if name}}no closing tag"
        val server = startHttpServer { exchange ->
            sendResponse(exchange, 200, brokenRemoteTemplate)
        }
        try {
            RemoteTemplateFetcher.clearCacheForTesting()
            registerConfigReader("markdown.template" to serverUrl(server))

            val context = ExportContext(
                project = project,
                endpoints = endpoints,
                channelConfig = MarkdownConfig(),
            )
            val result = channel.export(context)

            assertTrue("export should not be aborted by remote template parse failure",
                result is ExportResult.Success)
            val content = extractContent(result)
            assertTrue("should contain the distinguishable fallback line ",
                content.contains("Rendered with DEFAULT template (user template failed:"))
            assertTrue("fallback should contain default markers", content.contains("API Documentation"))
        } finally {
            server.stop(0)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun extractContent(result: ExportResult): String {
        val success = result as? ExportResult.Success
            ?: error("expected ExportResult.Success but got $result")
        val metadata = success.metadata as? MarkdownExportMetadata
            ?: error("expected MarkdownExportMetadata but got ${success.metadata}")
        return metadata.content
    }

    /**
     * Replaces the project's [ConfigReader] service with a [TestConfigReader] holding the
     * given key=value rules, then triggers a reload so listeners pick up the new config.
     */
    private fun registerConfigReader(vararg rules: Pair<String, String>) {
        val reader = TestConfigReader.fromRules(project, *rules)
        project.registerServiceInstance(ConfigReader::class.java, reader)
        runBlocking { reader.reload() }
    }

    /**
     * Starts a lightweight [HttpServer] on an ephemeral port with the given handler.
     * The caller MUST `server.stop(0)` in a `finally` block.
     */
    private fun startHttpServer(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            try {
                handler(exchange)
            } finally {
                exchange.close()
            }
        }
        server.executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        server.start()
        return server
    }

    /** Sends a UTF-8 response body with the given status code. */
    private fun sendResponse(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    /** Returns the URL of the running [server] (e.g. `http://localhost:12345/tpl`). */
    private fun serverUrl(server: HttpServer): String =
        "http://localhost:${server.address.port}/tpl"

    companion object {
        /** A minimal custom template that produces visibly different output from the default. */
        private const val CUSTOM_TEMPLATE = "CUSTOM TEMPLATE MARKER\n{{moduleName}}\n"
    }
}
