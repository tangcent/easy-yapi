package com.itangcent.easyapi.ai.credentials

import com.google.gson.reflect.TypeToken
import com.itangcent.easyapi.ai.AiProvider
import com.itangcent.easyapi.ai.credentials.DetectionResult.Hit
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.util.json.GsonUtils
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Result of a credential auto-detect scan.
 *
 * Sealed so callers (the settings UI + tests) must handle every case
 * explicitly — no silent fallthrough.
 */
sealed class DetectionResult {

    /** No credentials found on the local device. */
    object Miss : DetectionResult()

    /**
     * One credential found.
     *
     * @param provider Which provider to configure.
     * @param sourceLabel Human-readable source for the summary notification
     * (e.g. `"env var OPENAI_API_KEY"`, `"~/.claude/credentials.json"`).
     * Never contains the key itself.
     * @param apiKey The discovered key, or `null` for providers that don't
     * need one (Ollama, local LiteLLM proxy).
     * @param baseUrl Optional non-default base URL (e.g. Codex CLI's proxy).
     * @param model Optional model hint from the discovered config.
     */
    data class Hit(
        val provider: AiProvider,
        val sourceLabel: String,
        val apiKey: String?,
        val baseUrl: String? = null,
        val model: String? = null
    ) : DetectionResult()

    /**
     * Multiple credentials found; [primary] is pre-filled and [others] are
     * listed in the summary notification so the user can switch manually.
     */
    data class MultipleFound(
        val primary: Hit,
        val others: List<Hit>
    ) : DetectionResult()
}

/**
 * Scans the local device for AI provider credentials.
 *
 * Implementations MUST be side-effect-free: no writes, no env-var mutation,
 * no remote probes (localhost only). The scan returns a [DetectionResult];
 * the caller decides whether to persist.
 */
interface CredentialScanner {
    suspend fun scan(): DetectionResult
}

/**
 * Production scanner. Composes three injected probes (env / fs / http) so
 * unit tests can swap each independently.
 *
 * @param env Environment-variable map (default: `System.getenv()`).
 * @param fs Filesystem abstraction (default: [DefaultCredentialFileSystem]).
 * @param httpProbe HEAD probe (default: [DefaultLocalhostHttpProbe]).
 * @param userHome User's home directory path — file probes are resolved
 * relative to this and checked with `startsWith` after canonicalisation
 * (path-traversal guard).
 */
class DefaultCredentialScanner(
    private val env: Map<String, String> = System.getenv(),
    private val fs: CredentialFileSystem = DefaultCredentialFileSystem,
    private val httpProbe: LocalhostHttpProbe = DefaultLocalhostHttpProbe,
    private val userHome: String = System.getProperty("user.home")
) : CredentialScanner, IdeaLog {

    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

    override suspend fun scan(): DetectionResult = try {
        withTimeout(SCAN_TIMEOUT_MS.milliseconds) {
            coroutineScope {
                // Run the three probe groups in priority order. Each returns a
                // List<Hit> (may be empty). We keep all hits to build
                // MultipleFound when more than one is found.
                val envHits = probeEnvVars()
                val cliHits = probeCliConfigs()
                val serverHits = probeLocalServers()

                val allHits = envHits + cliHits + serverHits
                when {
                    allHits.isEmpty() -> DetectionResult.Miss
                    allHits.size == 1 -> allHits.single()
                    else -> DetectionResult.MultipleFound(
                        primary = allHits.first(),
                        others = allHits.drop(1)
                    )
                }
            }
        }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        // Fail closed: a hung probe must not crash the UI button handler.
        LOG.warn("CredentialScanner: scan timed out after ${SCAN_TIMEOUT_MS}ms", e)
        DetectionResult.Miss
    }

    // ---------------------------------------------------------------------------
    // Probe 1 — Environment variables (highest priority)
    // ---------------------------------------------------------------------------

    private fun probeEnvVars(): List<Hit> {
        val hits = mutableListOf<Hit>()
        env["OPENAI_API_KEY"]?.takeIf { it.isNotBlank() }?.let {
            hits += Hit(
                provider = AiProvider.OPENAI,
                sourceLabel = "env var OPENAI_API_KEY",
                apiKey = it
            )
        }
        env["ANTHROPIC_API_KEY"]?.takeIf { it.isNotBlank() }?.let {
            hits += Hit(
                provider = AiProvider.ANTHROPIC,
                sourceLabel = "env var ANTHROPIC_API_KEY",
                apiKey = it
            )
        }
        (env["GEMINI_API_KEY"] ?: env["GOOGLE_API_KEY"])?.takeIf { it.isNotBlank() }?.let {
            hits += Hit(
                provider = AiProvider.GEMINI,
                sourceLabel = "env var GEMINI_API_KEY",
                apiKey = it
            )
        }
        env["AZURE_OPENAI_API_KEY"]?.takeIf { it.isNotBlank() }?.let {
            val endpoint = env["AZURE_OPENAI_ENDPOINT"]
            hits += Hit(
                provider = AiProvider.AZURE_OPENAI,
                sourceLabel = "env var AZURE_OPENAI_API_KEY",
                apiKey = it,
                baseUrl = endpoint
            )
        }
        env["OLLAMA_HOST"]?.takeIf { it.isNotBlank() }?.let {
            // Ollama needs no key — its mere presence (even if pointing at the
            // default port) is a strong signal.
            hits += Hit(
                provider = AiProvider.OLLAMA,
                sourceLabel = "env var OLLAMA_HOST",
                apiKey = null,
                baseUrl = it
            )
        }
        return hits
    }

    // ---------------------------------------------------------------------------
    // Probe 2 — CLI tool config files (medium priority)
    // ---------------------------------------------------------------------------

    private suspend fun probeCliConfigs(): List<Hit> = coroutineScope {
        val probes = listOf(
            // Codex CLI stores an OpenAI key + base URL in config.json.
            async { readCodexCli() },
            // Claude Code CLI stores Anthropic credentials.
            async { readClaudeCli("~/.claude/credentials.json") },
            async { readClaudeCli("~/.config/claude/credentials.json") },
            // Plain OpenAI / Gemini key files.
            async { readKeyFile("~/.openai/api_key", AiProvider.OPENAI, "~/.openai/api_key") },
            async { readKeyFile("~/.config/openai/api_key.txt", AiProvider.OPENAI, "~/.config/openai/api_key.txt") },
            async { readKeyFile("~/.gemini/api_key", AiProvider.GEMINI, "~/.gemini/api_key") },
            async { readKeyFile("~/.config/gemini/api_key", AiProvider.GEMINI, "~/.config/gemini/api_key") },
            // Cursor — provider detected from stored key prefix.
            async { readCursorCredentials() }
        )
        probes.awaitAll().filterNotNull()
    }

    private fun readCodexCli(): Hit? {
        val text = readSafe("~/.codex/config.json") ?: return null
        val parsed = parseJsonMap(text) ?: return null

        @Suppress("UNCHECKED_CAST")
        val openai = parsed["openai"] as? Map<String, Any?> ?: parsed
        val key = openai["api_key"] as? String ?: openai["apiKey"] as? String
        val baseUrl = openai["base_url"] as? String ?: openai["baseUrl"] as? String
        if (key.isNullOrBlank()) return null
        return Hit(
            provider = AiProvider.OPENAI,
            sourceLabel = "~/.codex/config.json",
            apiKey = key,
            baseUrl = baseUrl
        )
    }

    private fun readClaudeCli(path: String): Hit? {
        val text = readSafe(path) ?: return null
        // credentials.json may be { "claudeApiKey": "..." } or a flat map.
        val parsed = parseJsonMap(text) ?: return null
        val key = parsed["claudeApiKey"] as? String
            ?: parsed["anthropicApiKey"] as? String
            ?: parsed["apiKey"] as? String
            ?: parsed["key"] as? String
        if (key.isNullOrBlank()) return null
        return Hit(
            provider = AiProvider.ANTHROPIC,
            sourceLabel = path,
            apiKey = key
        )
    }

    private fun readKeyFile(path: String, provider: AiProvider, label: String): Hit? {
        val text = readSafe(path) ?: return null
        val key = text.trim()
        if (key.isBlank() || key.contains('\n')) return null // multi-line => not a key file
        return Hit(
            provider = provider,
            sourceLabel = label,
            apiKey = key
        )
    }

    private fun readCursorCredentials(): Hit? {
        val path = "~/Library/Application Support/Cursor/User/globalStorage/cursor.credentials"
        val text = readSafe(path) ?: return null
        val parsed = parseJsonMap(text) ?: return null
        // Cursor stores keys under arbitrary provider names; probe known fields.
        val openai = parsed["openai"] as? String ?: parsed["openai.apiKey"] as? String
        val anthropic = parsed["anthropic"] as? String ?: parsed["anthropic.apiKey"] as? String
        return when {
            !openai.isNullOrBlank() -> Hit(
                provider = AiProvider.OPENAI,
                sourceLabel = path,
                apiKey = openai
            )

            !anthropic.isNullOrBlank() -> Hit(
                provider = AiProvider.ANTHROPIC,
                sourceLabel = path,
                apiKey = anthropic
            )

            else -> null
        }
    }

    // ---------------------------------------------------------------------------
    // Probe 3 — Running local servers (lowest priority)
    // ---------------------------------------------------------------------------

    private suspend fun probeLocalServers(): List<Hit> = coroutineScope {
        // Only run server probes if no env-var or CLI-config hit was found
        // for that server's provider — otherwise the localhost probe adds
        // nothing (the env/CLI hit is strictly more specific). We check
        // lazily because the env/CLI results are already in hand by the
        // time this runs.
        val ollamaAlreadyKnown = env.containsKey("OLLAMA_HOST")
        // LiteLLM proxy has no env-var sentinel; always probe if reachable.
        val probes = mutableListOf<Deferred<Hit?>>()
        if (!ollamaAlreadyKnown) {
            probes += async { probeServer("http://localhost:11434", AiProvider.OLLAMA, "Ollama on :11434") }
        }
        probes += async { probeServer("http://localhost:4000", AiProvider.CUSTOM, "LiteLLM proxy on :4000") }
        probes.awaitAll().filterNotNull()
    }

    private suspend fun probeServer(url: String, provider: AiProvider, label: String): Hit? {
        return if (httpProbe.head(url, LOCALHOST_PROBE_TIMEOUT_MS)) {
            Hit(
                provider = provider,
                sourceLabel = label,
                apiKey = null, // local servers need no key
                baseUrl = if (provider == AiProvider.OLLAMA) "$url/v1" else "$url/v1"
            )
        } else {
            null
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Reads a file under [userHome] safely. Returns `null` if the file is
     * absent, outside the user's home tree after canonicalisation, or any
     * I/O error occurs (fail closed).
     *
     * The [path] may use `~/` shorthand; we expand it.
     */
    private fun readSafe(path: String): String? {
        val expanded = expandTilde(path)
        val canonical = runCatching { File(expanded).canonicalPath }.getOrNull() ?: return null
        val homeCanonical = runCatching { File(userHome).canonicalPath }.getOrNull() ?: return null
        // Path-traversal guard: refuse anything that escapes
        // the user's home directory tree after canonicalisation.
        if (!canonical.startsWith(homeCanonical)) {
            LOG.warn("CredentialScanner: refusing to read path outside user home: $path")
            return null
        }
        // Pass the expanded (non-canonical) path to the filesystem so the
        // default impl and the fake fs in tests key on the same string.
        return fs.readFile(expanded)
    }

    /** Expands a leading `~/` to [userHome]. Leaves absolute paths untouched. */
    private fun expandTilde(path: String): String =
        if (path.startsWith("~/")) File(userHome, path.substring(2)).path else path

    /** Parses JSON to a `Map<String, Any?>`, failing closed on any error. */
    private fun parseJsonMap(text: String): Map<String, Any?>? = runCatching {
        GsonUtils.fromJson(text, mapType) as? Map<String, Any?>
    }.getOrNull()

    private companion object {
        /** Bounded scan duration so a hung localhost probe can't block the UI. */
        const val SCAN_TIMEOUT_MS = 10_000L

        /** Per-URL connect timeout for localhost HEAD probes. */
        const val LOCALHOST_PROBE_TIMEOUT_MS = 500L
    }
}
