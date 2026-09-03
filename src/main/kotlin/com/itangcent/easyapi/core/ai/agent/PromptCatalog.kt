package com.itangcent.easyapi.core.ai.agent

import com.itangcent.easyapi.core.logging.IdeaLog
import org.yaml.snakeyaml.Yaml
import java.util.concurrent.ConcurrentHashMap

/**
 * Optional scope constraint for a catalog file. A `null` field means
 * "applies to all" for that dimension.
 */
data class CatalogScope(
    val channel: String? = null,
    val format: String? = null,
    val framework: String? = null
) {
    /**
     * `true` when each non-null scope field is in the corresponding active
     * set. A file with no scope fields (`channel=null, format=null,
     * framework=null`) always matches.
     *
     * Matching is **case-insensitive** to bridge the gap between catalog
     * files (which conventionally use lowercase ids like `springmvc`) and
     * the recognizer-supplied framework labels (which use mixed case like
     * `SpringMVC`, `JAX-RS`, `gRPC`). Channel and format ids are already
     * lowercase by convention; the case-insensitive compare is a no-op for
     * them but keeps the contract uniform and robust.
     */
    fun matches(
        activeChannels: Set<String>,
        activeFormats: Set<String>,
        activeFrameworks: Set<String>
    ): Boolean {
        val lcChannels = activeChannels.mapTo(HashSet()) { it.lowercase() }
        val lcFormats = activeFormats.mapTo(HashSet()) { it.lowercase() }
        val lcFrameworks = activeFrameworks.mapTo(HashSet()) { it.lowercase() }
        return (channel == null || channel.lowercase() in lcChannels) &&
            (format == null || format.lowercase() in lcFormats) &&
            (framework == null || framework.lowercase() in lcFrameworks)
    }
}

/**
 * One catalog entry parsed from a file under `ai/detection/` or `ai/key-guides/`.
 *
 * @param category `"detection"` or `"key-guides"` (derived from the resource path)
 * @param id unique within `category`; for key-guide files, equals `key` by convention
 * @param title one-line human title
 * @param cue one-line "when to use"
 * @param key the rule key this file documents (key-guide files only; `null` for detection)
 * @param scope optional channel/format/framework constraint
 * @param resourcePath classpath path of the source file (e.g. `ai/key-guides/postman.test.md`)
 */
data class CatalogEntry(
    val category: String,
    val id: String,
    val title: String,
    val cue: String,
    val key: String?,
    val scope: CatalogScope,
    val resourcePath: String
)

/**
 * File-based catalog of detection and key-guide prompts under
 * `src/main/resources/ai/`.
 *
 * Each catalog file begins with a YAML front-matter header (delimited by
 * `---` lines) carrying `id`, `title`, `cue` (required), `key` (key-guides
 * only), and optional `channel`/`format`/`framework` scope. The header is
 * parsed via SnakeYAML; the markdown body follows the closing `---`.
 *
 * Discovery is driven by `ai/catalog-manifest.txt` (one file path per line,
 * relative to `src/main/resources/`). This avoids any JarFile/Path coupling
 * in production code.
 *
 * ## Robustness
 *
 * Malformed YAML, a missing required field, or an unreadable file → the
 * entry is skipped with `LOG.warn` (never throws). An empty/missing
 * manifest → `list(...)` returns `emptyList()` + `LOG.warn`.
 *
 * ## Caching
 *
 * Parsed entries are loaded once (lazy) and cached. Body strings are loaded
 * on demand and cached in a [ConcurrentHashMap]. Files are immutable in the
 * JAR, so no invalidation is needed.
 */
object PromptCatalog : IdeaLog {

    private const val MANIFEST_RESOURCE = "/ai/catalog-manifest.txt"

    /** Parsed entries grouped by category, loaded once on first access. */
    private val entriesByCategory: Map<String, List<CatalogEntry>> by lazy { loadCatalog() }

    /** Cached bodies, keyed by `"$category/$id"`. Only non-null results are cached. */
    private val bodyCache: ConcurrentHashMap<String, String> = ConcurrentHashMap()

    /** All entries in [category], or an empty list if the category is empty/unknown. */
    fun list(category: String): List<CatalogEntry> =
        entriesByCategory[category] ?: emptyList()

    /** The entry for [id] within [category], or `null` if not found. */
    fun entry(category: String, id: String): CatalogEntry? =
        entriesByCategory[category]?.firstOrNull { it.id == id }

    /**
     * The markdown body for [id] within [category], or `null` if not found.
     * Cached after first access.
     *
     * `null` results are NOT cached ([ConcurrentHashMap] rejects null values),
     * so a miss re-resolves on each call — cheap since [entry] is a list scan
     * over already-parsed headers.
     */
    fun body(category: String, id: String): String? {
        val cacheKey = "$category/$id"
        // Fast path: already cached.
        bodyCache[cacheKey]?.let { return it }
        // Miss → resolve and cache only when non-null.
        val resolved = entry(category, id)?.let { loadBody(it.resourcePath) }
        if (resolved != null) {
            bodyCache[cacheKey] = resolved
        }
        return resolved
    }

    /**
     * Entries in [category] whose [CatalogScope] matches the supplied active
     * feature sets. Used by [SystemPromptBuilder] to build enablement-aware
     * indexes for the Reactive path.
     */
    fun listFor(
        category: String,
        activeChannels: Set<String>,
        activeFormats: Set<String>,
        activeFrameworks: Set<String>
    ): List<CatalogEntry> =
        list(category).filter {
            it.scope.matches(activeChannels, activeFormats, activeFrameworks)
        }

    // -------------------------------------------------------------------
    // Internal parsing helpers (pure — testable without classpath access)
    // -------------------------------------------------------------------

    /**
     * Splits catalog file content into the YAML front-matter header and the
     * markdown body. Returns `null` if the content does not start with a
     * valid `---`-delimited header or the YAML is malformed.
     *
     * @param content the raw file content (UTF-8)
     * @param yaml the [Yaml] instance to use for parsing (injectable for tests)
     */
    internal fun parseFrontMatter(content: String, yaml: Yaml = Yaml()): ParsedCatalogFile? {
        val lines = content.lines()
        if (lines.isEmpty() || lines[0].trim() != "---") return null
        // Skip the opening `---` at index 0; find the closing `---` after it.
        val closeIndex = (1 until lines.size).firstOrNull { lines[it].trim() == "---" } ?: -1
        if (closeIndex == -1) return null
        val yamlText = lines.subList(1, closeIndex).joinToString("\n")
        val body = lines.subList(closeIndex + 1, lines.size)
            .joinToString("\n").trim()
        val header: Map<String, Any?> = try {
            @Suppress("UNCHECKED_CAST")
            yaml.load<Map<String, Any?>>(yamlText) ?: emptyMap()
        } catch (e: Exception) {
            return null
        }
        return ParsedCatalogFile(header, body)
    }

    /**
     * Builds a [CatalogEntry] from a parsed header, deriving the category
     * from the resource path. Returns `null` if a required field (`id`,
     * `title`, `cue`) is missing or the category cannot be derived.
     */
    internal fun buildEntry(resourcePath: String, parsed: ParsedCatalogFile): CatalogEntry? {
        val header = parsed.header
        val id = header["id"] as? String ?: return null
        val title = header["title"] as? String ?: return null
        val cue = header["cue"] as? String ?: return null
        val key = header["key"] as? String
        val channel = header["channel"] as? String
        val format = header["format"] as? String
        val framework = header["framework"] as? String
        val category = deriveCategory(resourcePath) ?: return null
        return CatalogEntry(
            category = category,
            id = id,
            title = title,
            cue = cue,
            key = key,
            scope = CatalogScope(channel, format, framework),
            resourcePath = resourcePath
        )
    }

    /**
     * Derives the category (`"detection"` or `"key-guides"`) from a resource
     * path like `ai/detection/spring-filters-interceptors.md`. Returns `null`
     * if the path doesn't follow the `ai/<category>/...` pattern.
     */
    internal fun deriveCategory(resourcePath: String): String? {
        val parts = resourcePath.split("/")
        return if (parts.size >= 2 && parts[0] == "ai") parts[1] else null
    }

    /** Parsed front-matter + body pair (internal transport type). */
    internal data class ParsedCatalogFile(
        val header: Map<String, Any?>,
        val body: String
    )

    // -------------------------------------------------------------------
    // Classpath loading (production path)
    // -------------------------------------------------------------------

    private fun loadCatalog(): Map<String, List<CatalogEntry>> {
        val manifestText = javaClass.getResourceAsStream(MANIFEST_RESOURCE)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: run {
            LOG.warn("catalog-manifest.txt not found on classpath; catalog is empty")
            return emptyMap()
        }

        val yaml = Yaml()
        val result = mutableMapOf<String, MutableList<CatalogEntry>>()
        manifestText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { path ->
                val entry = loadEntry("/$path", yaml)
                if (entry != null) {
                    result.getOrPut(entry.category) { mutableListOf() }.add(entry)
                }
            }
        return result
    }

    private fun loadEntry(resourcePath: String, yaml: Yaml): CatalogEntry? {
        val content = javaClass.getResourceAsStream(resourcePath)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: run {
            LOG.warn("catalog file not found on classpath: $resourcePath")
            return null
        }
        val parsed = parseFrontMatter(content, yaml) ?: run {
            LOG.warn("skipping catalog file (malformed front-matter): $resourcePath")
            return null
        }
        return buildEntry(resourcePath.removePrefix("/"), parsed) ?: run {
            LOG.warn("skipping catalog file (missing required field): $resourcePath")
            return null
        }
    }

    private fun loadBody(resourcePath: String): String? {
        val fullResourcePath = if (resourcePath.startsWith("/")) resourcePath else "/$resourcePath"
        val content = javaClass.getResourceAsStream(fullResourcePath)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: run {
            LOG.warn("catalog body not found on classpath: $fullResourcePath")
            return null
        }
        return parseFrontMatter(content)?.body ?: content.trim()
    }
}
