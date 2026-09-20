package com.itangcent.easyapi.core.extension

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.core.logging.IdeaLog

/**
 * The built-in extension catalogue: every extension config shipped under the
 * `extensions` resource directory, loaded once from the classpath.
 *
 * An extension is switched on or off through a **code list** — a comma-separated
 * list of extension codes in which every entry has one of three meanings:
 *
 * - `<code>` — explicitly enabled, even when the extension is not `defaultEnabled`;
 * - `-<code>` — explicitly disabled; the only way to turn off an extension that
 *   *is* `defaultEnabled`;
 * - absent — falls back to the extension's own `defaultEnabled` flag.
 *
 * One and the same list is what the Extensions tab edits and what the rule engine
 * consumes, persisted verbatim in `RuleFileSettings.extensionConfigs`. A code that
 * appears both positively and as an exclusion (`spring,-spring`) counts as enabled,
 * because [enabledExtensions] ORs the two branches.
 *
 * This object is the **only** owner of that grammar, and of the
 * [ExtensionConfig.defaultEnabled] flag the grammar reads. Deciding whether a code
 * is on ([enabledExtensions]), projecting that decision onto codes
 * ([selectedCodes]), onto rule text ([buildConfig]) or onto the default set
 * ([defaultCodes]), and turning a user selection back into a list
 * ([encodeSelection]) all happen here. Everything outside holds a code list and
 * delegates: the settings module owns the field but not its grammar, and
 * `ExtensionConfigSource` owns only the `on-class` availability filter it layers
 * on top. Keeping the decision in one expression is what makes it impossible for
 * the tab and the rule engine to disagree — the shape issue #1461 came from.
 */
object ExtensionConfigRegistry : IdeaLog {

    private var extensions: List<ExtensionConfig> = emptyList()

    private const val EXTENSIONS_DIR = "extensions"

    init {
        loadExtensions()
    }

    /**
     * Loads extension configs from the classpath.
     *
     * @param project When non-null, channel-specific config files (contributed
     *   via the [ChannelRegistry] EP) are appended to the fallback
     *   file list. When `null` (e.g. in unit tests), only the shared base
     *   configs are loaded.
     */
    fun loadExtensions(project: Project? = null) {
        val extensionList = mutableListOf<ExtensionConfig>()
        try {
            val loader = javaClass.classLoader
            val extensionsUrl = loader.getResource(EXTENSIONS_DIR)

            if (extensionsUrl != null) {
                val extensionFiles = loadExtensionFiles(extensionsUrl, loader, project)
                for (fileContent in extensionFiles) {
                    val config = ExtensionConfigParser.parse(fileContent)
                    if (config != null) {
                        extensionList.add(config)
                    }
                }
            }

            extensions = extensionList
            LOG.info("Loaded ${extensions.size} extensions")
        } catch (e: Exception) {
            LOG.warn("Failed to load extensions", e)
            extensions = emptyList()
        }
    }

    private fun loadExtensionFiles(
        extensionsUrl: java.net.URL,
        loader: ClassLoader,
        project: Project? = null
    ): List<String> {
        val files = mutableListOf<String>()
        try {
            val jarFile = resolveJarFile(extensionsUrl)
            if (jarFile != null) {
                // Resource is packaged inside a JAR (e.g. a bundled plugin). Enumerate
                // every `extensions/*.config` entry so channel-specific configs are
                // picked up regardless of which channel contributed them.
                val entries = jarFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("$EXTENSIONS_DIR/") && entry.name.endsWith(".config")) {
                        loader.getResourceAsStream(entry.name)?.use { stream ->
                            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                                files.add(reader.readText())
                            }
                        }
                    }
                }
            } else {
                // Try to load from a plain directory on disk.
                var dir: java.io.File? = null
                try {
                    dir = java.io.File(extensionsUrl.toURI())
                } catch (e: IllegalArgumentException) {
                    // URI is not hierarchical - try using URL path or file protocol
                    if (extensionsUrl.protocol == "file") {
                        dir = java.io.File(extensionsUrl.file)
                    }
                }

                if (dir != null && dir.isDirectory) {
                    dir.listFiles()?.filter { it.extension == "config" }?.forEach { file ->
                        files.add(file.readText(Charsets.UTF_8))
                    }
                } else {
                    // Last-resort fallback: probe the known config files directly from
                    // the classpath. This is only reached when neither the JAR nor the
                    // directory can be enumerated (rare; mostly edge cases in unit
                    // tests with unusual classloaders).
                    for (extName in knownExtensionNames(project)) {
                        loader.getResourceAsStream("$EXTENSIONS_DIR/$extName.config")?.use { stream ->
                            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                                files.add(reader.readText())
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to load extension files", e)
        }
        return files
    }

    /**
     * Resolves the [java.util.jar.JarFile] that backs a `jar:`/`file:` resource URL,
     * or `null` if the URL does not point into a JAR.
     *
     * The JDK's own [java.net.JarURLConnection] is used when available. When running
     * inside IntelliJ, `openConnection()` returns the platform's own
     * `com.intellij.util.lang.ZipResourceFile$MyUrlConnection` (which is **not** a
     * `JarURLConnection`), so for `jar:` URLs we parse the nested file path manually
     * and open the JAR directly.
     */
    private fun resolveJarFile(extensionsUrl: java.net.URL): java.util.jar.JarFile? {
        return try {
            val connection = extensionsUrl.openConnection()
            if (connection is java.net.JarURLConnection) {
                connection.jarFile
            } else if (extensionsUrl.protocol == "jar") {
                // URL form: jar:file:/path/to/x.jar!/extensions
                val path = extensionsUrl.path // file:/path/to/x.jar!/extensions
                val bangIndex = path.indexOf('!')
                val filePart = if (bangIndex >= 0) path.substring(0, bangIndex) else path
                // URI.getPath() (unlike the deprecated URL.getFile()) also decodes
                // percent-escapes, so jar paths containing spaces still resolve.
                val filePath = java.net.URI(filePart).path
                java.util.jar.JarFile(filePath)
            } else {
                null
            }
        } catch (e: Exception) {
            LOG.warn("Failed to resolve jar for $extensionsUrl", e)
            null
        }
    }

    /**
     * The hardcoded fallback list of general (channel-agnostic) extension names,
     * augmented with channel-specific configs contributed via the
     * [ChannelRegistry] EP when a [project] is available.
     *
     * Note: this list is only used by the last-resort classpath probe in
     * [loadExtensionFiles]. The primary code paths enumerate the JAR/directory
     * directly, which discovers all `.config` files (including channel-specific
     * ones) without needing to be listed here.
     */
    private fun knownExtensionNames(project: Project?): List<String> {
        val general = listOf(
            "swagger", "swagger3", "jackson", "gson", "fastjson",
            "spring", "spring-validations", "spring-webflux", "spring-configuration", "spring-properties",
            "ignore", "deprecated", "jakarta-validation", "javax-validation", "converts",
            "field-utils",
            "field-order-alphabetically", "field-order-alphabetically-desc",
            "field-order-child-first", "field-order-parent-first",
            "jakarta-validation-strict", "javax-validation-strict",
            "mybatis-plus"
        )
        val channelConfigs = project?.let {
            runCatching { ChannelRegistry.getInstance(it).configFiles() }
                .getOrDefault(emptyList())
        } ?: emptyList()
        return general + channelConfigs
    }

    /** The catalogue in load order. Every code list below keeps this order. */
    fun allExtensions(): List<ExtensionConfig> = extensions

    /** The extension registered under [code], or `null` when no `.config` declares it. */
    fun getExtension(code: String): ExtensionConfig? = extensions.find { it.code == code }

    /** Every known code, in catalogue order, regardless of `defaultEnabled`. */
    fun codes(): Array<String> = extensions.map { it.code }.toTypedArray()

    /**
     * Whether [codes] switches [extension] on — the whole grammar, in one
     * expression.
     *
     * This is the only place in `src/main` that reads
     * [ExtensionConfig.defaultEnabled]; every query below is a projection of it and
     * nothing outside this object may consult the flag itself (pinned by
     * `ExtensionDefaultEnabledOwnershipGuardTest`). A positive entry wins over an
     * exclusion for the same code, because the two branches are ORed.
     */
    private fun isEnabled(extension: ExtensionConfig, codes: Set<String>): Boolean =
        codes.contains(extension.code) ||
                (extension.defaultEnabled && !codes.contains("-${extension.code}"))

    /**
     * The extensions enabled by [codes], in catalogue order, with the `-<code>`
     * exclusions already resolved — the primitive every other query is built on.
     *
     * An extension whose `on-class` is missing from the project classpath is still
     * returned: availability is a project-scoped question, so
     * `ExtensionConfigSource` — the only holder of a `Project` — filters this
     * result instead.
     */
    fun enabledExtensions(codes: Array<String>): List<ExtensionConfig> {
        val set = codes.toSet()
        return extensions.filter { isEnabled(it, set) }
    }

    /**
     * The enabled codes for [codes], in catalogue order, with the `-<code>`
     * exclusions already resolved — [enabledExtensions] projected onto codes.
     *
     * The result is always positive and registry-ordered, so two selections can be
     * compared as lists. It is a projection for reading only: persisting it back via
     * [codesToString] would drop every exclusion and let the `defaultEnabled`
     * fallback switch the excluded extension on again.
     */
    fun selectedCodes(codes: Array<String>): Array<String> =
        enabledExtensions(codes).map { it.code }.toTypedArray()

    /**
     * The codes of the extensions that are enabled by default — what an empty code
     * list reads as.
     *
     * Derived from [enabledExtensions] rather than read off the flag, because with
     * an empty list both OR branches collapse to `defaultEnabled`. "An empty list
     * means the defaults" is therefore not a special case of the grammar; it is a
     * consequence of it.
     */
    fun defaultCodes(): Array<String> =
        enabledExtensions(emptyArray()).map { it.code }.toTypedArray()

    /**
     * The rule text of every enabled extension, joined by [separator] —
     * [enabledExtensions] projected onto [ExtensionConfig.content].
     *
     * @param selectedCodes A code list, filtered by the grammar described on this
     *   object. An empty array means "nothing recorded yet" and enables exactly
     *   [defaultCodes]; a non-empty array overrides that default entry by entry.
     * @return The concatenated rule text, or `""` when nothing is enabled.
     */
    fun buildConfig(selectedCodes: Array<String>, separator: CharSequence = "\n"): String =
        enabledExtensions(selectedCodes).joinToString(separator) { it.content }

    /**
     * Encodes a set of user-checked codes into the persisted code list — the
     * inverse of [selectedCodes], and the encode side of the grammar.
     *
     * Checked extensions are written as plain codes. An extension that the user
     * unchecked but that is enabled by default must be written as an explicit
     * `-<code>` exclusion: writing only the checked codes would drop the
     * deselection, and the next read would fall back to `defaultEnabled` and
     * silently re-check it (issue #1461). Unchecked extensions that are disabled by
     * default need no entry.
     *
     * Compares against [defaultCodes] instead of the flag, so that the flag is read
     * in exactly one expression. [codesToString] is deliberately kept as the plain
     * (unencoded) joiner it has always been — it serves the field's default value,
     * where every entry is a positive code.
     */
    fun encodeSelection(checkedCodes: Collection<String>): String {
        val checked = checkedCodes.toSet()
        val defaults = defaultCodes().toSet()
        return extensions
            .filter { it.code.isNotBlank() && (checked.contains(it.code) || defaults.contains(it.code)) }
            .joinToString(",") { if (checked.contains(it.code)) it.code else "-${it.code}" }
    }

    /**
     * Returns [codes] with each of [code] enabled: the code is added positively and
     * any `-<code>` exclusion for it is dropped. Blank entries are removed.
     */
    fun addSelectedConfig(codes: Array<String>, vararg code: String): Array<String> {
        val set = LinkedHashSet(codes.toList())
        set.addAll(code.map { it.trim() })
        code.map { "-${it.trim()}" }.forEach { set.remove(it) }
        return set.filter { it.isNotBlank() }.toTypedArray()
    }

    /**
     * Returns [codes] with each of [code] disabled: the code is removed and an
     * explicit `-<code>` exclusion is added, which is what keeps a `defaultEnabled`
     * extension off on the next read. Blank entries are removed.
     *
     * An exclusion is written even for a code that is not `defaultEnabled`. Such an
     * entry changes nothing on read (the code is off when absent); it only matters
     * if the extension later becomes enabled by default.
     */
    fun removeSelectedConfig(codes: Array<String>, vararg code: String): Array<String> {
        val set = LinkedHashSet(codes.toList())
        set.removeAll(code.map { it.trim() }.toSet())
        code.map { "-${it.trim()}" }.forEach { set.add(it) }
        return set.filter { it.isNotBlank() }.toTypedArray()
    }

    /**
     * Joins [codes] with commas and no encoding at all — an entry that is already an
     * exclusion stays one.
     *
     * Only lossless for lists that are entirely positive, which is why it serves the
     * default value (`codesToString(defaultCodes())`) and not a user selection: a
     * deselection has to be encoded, and [encodeSelection] is what does that.
     */
    fun codesToString(codes: Array<String>): String {
        return codes.filter { it.isNotBlank() }.joinToString(",")
    }

    /** Splits a persisted code list back into codes, dropping blanks. Inverse of [codesToString]. */
    fun stringToCodes(codes: String): Array<String> {
        return codes.split(",").map { it.trim() }.filter { it.isNotBlank() }.toTypedArray()
    }
}
