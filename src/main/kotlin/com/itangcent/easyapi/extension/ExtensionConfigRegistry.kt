package com.itangcent.easyapi.extension

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.exporter.channel.ChannelRegistry
import com.itangcent.easyapi.logging.IdeaLog

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
                val filePath = java.net.URL(filePart).file
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

    fun allExtensions(): List<ExtensionConfig> = extensions

    fun getExtension(code: String): ExtensionConfig? = extensions.find { it.code == code }

    fun codes(): Array<String> = extensions.map { it.code }.toTypedArray()

    fun defaultCodes(): Array<String> {
        return extensions
            .filter { it.defaultEnabled }
            .map { it.code }
            .toTypedArray()
    }

    fun buildConfig(selectedCodes: Array<String>, separator: CharSequence = "\n"): String {
        if (selectedCodes.isEmpty()) {
            return extensions
                .filter { it.defaultEnabled }
                .joinToString(separator) { it.content }
        }

        val set = selectedCodes.toSet()
        return extensions
            .filter { set.contains(it.code) || (it.defaultEnabled && !set.contains("-${it.code}")) }
            .joinToString(separator) { it.content }
    }

    fun selectedCodes(codes: Array<String>): Array<String> {
        val set = codes.toSet()
        return extensions
            .filter { set.contains(it.code) || (it.defaultEnabled && !set.contains("-${it.code}")) }
            .map { it.code }
            .toTypedArray()
    }

    fun addSelectedConfig(codes: Array<String>, vararg code: String): Array<String> {
        val set = LinkedHashSet(codes.toList())
        set.addAll(code.map { it.trim() })
        code.map { "-${it.trim()}" }.forEach { set.remove(it) }
        return set.filter { it.isNotBlank() }.toTypedArray()
    }

    fun removeSelectedConfig(codes: Array<String>, vararg code: String): Array<String> {
        val set = LinkedHashSet(codes.toList())
        set.removeAll(code.map { it.trim() }.toSet())
        code.map { "-${it.trim()}" }.forEach { set.add(it) }
        return set.filter { it.isNotBlank() }.toTypedArray()
    }

    fun codesToString(codes: Array<String>): String {
        return codes.filter { it.isNotBlank() }.joinToString(",")
    }

    fun stringToCodes(codes: String): Array<String> {
        return codes.split(",").map { it.trim() }.filter { it.isNotBlank() }.toTypedArray()
    }
}
