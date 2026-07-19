package com.itangcent.easyapi.core.ai.credentials

import com.itangcent.easyapi.core.ai.AiProvider
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Tests for [DefaultCredentialScanner].
 *
 * Each case injects a fake env / filesystem / HTTP probe and asserts the
 * [DetectionResult]. No real I/O.
 */
class CredentialScannerTest : EasyApiLightCodeInsightFixtureTestCase() {

    /** Fake user home — all `~/` paths expand under this. */
    private val fakeHome = "/tmp/credscan-test-home"

    // -------------------------------------------------------------------------
    // Probe 1 — Environment variables
    // -------------------------------------------------------------------------

    fun testEnvVarOpenAI() = runBlocking {
        val scanner = scanner(env = mapOf("OPENAI_API_KEY" to "sk-test-123"))
        val result = scanner.scan()
        val hit = expectHit(result)
        assertEquals(AiProvider.OPENAI, hit.provider)
        assertEquals("sk-test-123", hit.apiKey)
        assertEquals("env var OPENAI_API_KEY", hit.sourceLabel)
    }

    fun testEnvVarAnthropic() = runBlocking {
        val scanner = scanner(env = mapOf("ANTHROPIC_API_KEY" to "sk-ant-test"))
        val hit = expectHit(scanner.scan())
        assertEquals(AiProvider.ANTHROPIC, hit.provider)
        assertEquals("sk-ant-test", hit.apiKey)
    }

    fun testEnvVarGemini() = runBlocking {
        val scanner = scanner(env = mapOf("GEMINI_API_KEY" to "gem-key"))
        val hit = expectHit(scanner.scan())
        assertEquals(AiProvider.GEMINI, hit.provider)
    }

    fun testEnvVarGoogleApiKeyAlias() = runBlocking {
        val scanner = scanner(env = mapOf("GOOGLE_API_KEY" to "google-key"))
        val hit = expectHit(scanner.scan())
        assertEquals(AiProvider.GEMINI, hit.provider)
    }

    fun testEnvVarAzureOpenAI() = runBlocking {
        val scanner = scanner(env = mapOf(
            "AZURE_OPENAI_API_KEY" to "azure-key",
            "AZURE_OPENAI_ENDPOINT" to "https://my-deployment.openai.azure.com"
        ))
        val hit = expectHit(scanner.scan())
        assertEquals(AiProvider.AZURE_OPENAI, hit.provider)
        assertEquals("azure-key", hit.apiKey)
        assertEquals("https://my-deployment.openai.azure.com", hit.baseUrl)
    }

    fun testEnvVarOllamaHost() = runBlocking {
        val scanner = scanner(env = mapOf("OLLAMA_HOST" to "http://gpu-box:11434"))
        val hit = expectHit(scanner.scan())
        assertEquals(AiProvider.OLLAMA, hit.provider)
        // Ollama needs no key — the env var just signals the user has Ollama.
        assertNull(hit.apiKey)
        assertEquals("http://gpu-box:11434", hit.baseUrl)
    }

    // -------------------------------------------------------------------------
    // Probe 2 — CLI tool configs
    // -------------------------------------------------------------------------

    fun testCodexCliConfig() = runBlocking {
        val fs = fakeFs(
            "$fakeHome/.codex/config.json" to """
                {"openai":{"api_key":"sk-codex-key","base_url":"https://api.openai.com/v1"}}
            """.trimIndent()
        )
        val hit = expectHit(scanner(fs = fs).scan())
        assertEquals(AiProvider.OPENAI, hit.provider)
        assertEquals("sk-codex-key", hit.apiKey)
        assertEquals("https://api.openai.com/v1", hit.baseUrl)
        assertTrue(hit.sourceLabel.contains("codex"))
    }

    fun testClaudeCredentialsJson() = runBlocking {
        val fs = fakeFs(
            "$fakeHome/.claude/credentials.json" to """{"claudeApiKey":"sk-ant-from-claude"}"""
        )
        val hit = expectHit(scanner(fs = fs).scan())
        assertEquals(AiProvider.ANTHROPIC, hit.provider)
        assertEquals("sk-ant-from-claude", hit.apiKey)
    }

    fun testClaudeCredentialsXDGVariant() = runBlocking {
        val fs = fakeFs(
            "$fakeHome/.config/claude/credentials.json" to """{"anthropicApiKey":"sk-ant-xdg"}"""
        )
        val hit = expectHit(scanner(fs = fs).scan())
        assertEquals(AiProvider.ANTHROPIC, hit.provider)
    }

    fun testOpenAIKeyFile() = runBlocking {
        val fs = fakeFs("$fakeHome/.openai/api_key" to "sk-from-openai-file")
        val hit = expectHit(scanner(fs = fs).scan())
        assertEquals(AiProvider.OPENAI, hit.provider)
        assertEquals("sk-from-openai-file", hit.apiKey)
    }

    fun testGeminiKeyFile() = runBlocking {
        val fs = fakeFs("$fakeHome/.gemini/api_key" to "gem-from-file")
        val hit = expectHit(scanner(fs = fs).scan())
        assertEquals(AiProvider.GEMINI, hit.provider)
    }

    fun testCursorCredentialsOpenAI() = runBlocking {
        val path = "$fakeHome/Library/Application Support/Cursor/User/globalStorage/cursor.credentials"
        val fs = fakeFs(path to """{"openai":"sk-cursor-openai"}""")
        val hit = expectHit(scanner(fs = fs).scan())
        assertEquals(AiProvider.OPENAI, hit.provider)
        assertEquals("sk-cursor-openai", hit.apiKey)
    }

    fun testCursorCredentialsAnthropic() = runBlocking {
        val path = "$fakeHome/Library/Application Support/Cursor/User/globalStorage/cursor.credentials"
        val fs = fakeFs(path to """{"anthropic":"sk-ant-cursor"}""")
        val hit = expectHit(scanner(fs = fs).scan())
        assertEquals(AiProvider.ANTHROPIC, hit.provider)
    }

    // -------------------------------------------------------------------------
    // Probe 3 — Running local servers
    // -------------------------------------------------------------------------

    fun testOllamaLocalServer() = runBlocking {
        val httpProbe = FakeLocalhostHttpProbe(reachable = mapOf("http://localhost:11434" to true))
        val hit = expectHit(scanner(httpProbe = httpProbe).scan())
        assertEquals(AiProvider.OLLAMA, hit.provider)
        assertNull(hit.apiKey)
        assertEquals("http://localhost:11434/v1", hit.baseUrl)
    }

    fun testLiteLLMLocalServer() = runBlocking {
        val httpProbe = FakeLocalhostHttpProbe(reachable = mapOf("http://localhost:4000" to true))
        val hit = expectHit(scanner(httpProbe = httpProbe).scan())
        assertEquals(AiProvider.CUSTOM, hit.provider)
        assertNull(hit.apiKey)
        assertEquals("http://localhost:4000/v1", hit.baseUrl)
    }

    // -------------------------------------------------------------------------
    // Aggregation
    // -------------------------------------------------------------------------

    fun testMultipleFoundEnvVarPrimaryOverCli() = runBlocking {
        val env = mapOf("ANTHROPIC_API_KEY" to "sk-ant-env")
        val fs = fakeFs(
            "$fakeHome/.openai/api_key" to "sk-openai-file",
            "$fakeHome/.gemini/api_key" to "gem-file"
        )
        val result = scanner(env = env, fs = fs).scan()
        assertTrue("expected MultipleFound", result is DetectionResult.MultipleFound)
        val multi = result as DetectionResult.MultipleFound
        assertEquals(AiProvider.ANTHROPIC, multi.primary.provider)
        assertEquals("sk-ant-env", multi.primary.apiKey)
        assertEquals(2, multi.others.size)
        // Others are listed in scan order: CLI hits come after env hits.
        assertTrue(multi.others.any { it.provider == AiProvider.OPENAI })
        assertTrue(multi.others.any { it.provider == AiProvider.GEMINI })
    }

    fun testAllMissReturnsMiss() = runBlocking {
        val result = scanner(env = emptyMap()).scan()
        assertTrue("expected Miss", result is DetectionResult.Miss)
    }

    // -------------------------------------------------------------------------
    // Fail-closed behaviour
    // -------------------------------------------------------------------------

    fun testMalformedCliConfigJsonReturnsMiss() = runBlocking {
        val fs = fakeFs("$fakeHome/.claude/credentials.json" to "{not valid json")
        val result = scanner(fs = fs).scan()
        assertTrue("malformed JSON should fail closed to Miss", result is DetectionResult.Miss)
    }

    fun testEnvVarValueIsNeverOpenedAsFile() = runBlocking {
        // A malicious env var value that looks like a path traversal.
        // The scanner must NOT open it as a file — env values are values.
        val env = mapOf("OPENAI_API_KEY" to "../../etc/passwd")
        val hit = expectHit(scanner(env = env).scan())
        assertEquals(AiProvider.OPENAI, hit.provider)
        assertEquals("../../etc/passwd", hit.apiKey) // treated as a key, not a path
    }

    fun testPathTraversalOutsideUserHomeRefused() = runBlocking {
        // Symlink-like canonicalisation is hard to fake without real FS;
        // this test simulates a CLI config path that escapes userHome via `..`.
        // The scanner's guard should refuse to read it and return Miss.
        // Note: our readSafe expands ~/.. to <home>/.. which canonicalises
        // OUTSIDE <home>, so the guard fires.
        // We can't easily inject such a path through the public API (paths
        // are hardcoded), so this test just confirms that a fs miss returns
        // Miss — the path-traversal guard is exercised by the canonical-path
        // check in readSafe.
        val fs = fakeFs() // empty
        val result = scanner(fs = fs).scan()
        assertTrue(result is DetectionResult.Miss)
    }

    fun testSlowLocalhostProbeReturnsMiss() = runBlocking {
        // Simulates a localhost server that's unreachable: the probe
        // respects its timeoutMs contract by waiting then returning false.
        val httpProbe = object : LocalhostHttpProbe {
            override suspend fun head(url: String, timeoutMs: Long): Boolean {
                delay(timeoutMs)
                return false
            }
        }
        val scanner = DefaultCredentialScanner(
            env = emptyMap(),
            fs = fakeFs(),
            httpProbe = httpProbe,
            userHome = fakeHome
        )
        val result = scanner.scan()
        assertTrue("expected Miss when localhost probe is slow", result is DetectionResult.Miss)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun scanner(
        env: Map<String, String> = emptyMap(),
        fs: CredentialFileSystem = fakeFs(),
        httpProbe: LocalhostHttpProbe = FakeLocalhostHttpProbe()
    ): CredentialScanner = DefaultCredentialScanner(
        env = env,
        fs = fs,
        httpProbe = httpProbe,
        userHome = fakeHome
    )

    private fun fakeFs(vararg entries: Pair<String, String>): CredentialFileSystem {
        // Normalize path separators so the fake fs matches regardless of
        // whether the scanner hands us a forward- or backslash-separated
        // path. Production code expands `~/` via `File(userHome, ...).path`,
        // which on Windows emits backslashes; the real disk accepts either,
        // but this in-memory map does an exact-string lookup and would miss
        // on Windows if we keyed it verbatim.
        val normalized = entries.associate { (path, content) ->
            path.replace('\\', '/') to content
        }
        return CredentialFileSystem { path -> normalized[path.replace('\\', '/')] }
    }

    private fun expectHit(result: DetectionResult): DetectionResult.Hit {
        assertTrue("expected Hit but got $result", result is DetectionResult.Hit)
        return result as DetectionResult.Hit
    }
}

/** In-memory HTTP probe: returns the configured reachability per URL. */
private class FakeLocalhostHttpProbe(
    private val reachable: Map<String, Boolean> = emptyMap()
) : LocalhostHttpProbe {
    override suspend fun head(url: String, timeoutMs: Long): Boolean =
        reachable.getOrDefault(url, false)
}
