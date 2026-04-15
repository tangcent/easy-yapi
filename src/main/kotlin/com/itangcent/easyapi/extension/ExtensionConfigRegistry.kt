package com.itangcent.easyapi.extension

import com.itangcent.easyapi.logging.IdeaLog

object ExtensionConfigRegistry : IdeaLog {

    private var extensions: List<ExtensionConfig> = emptyList()

    private const val EXTENSIONS_DIR = "extensions"

    init {
        loadExtensions()
    }

    fun loadExtensions() {
        val extensionList = mutableListOf<ExtensionConfig>()
        try {
            val loader = javaClass.classLoader
            val extensionsUrl = loader.getResource(EXTENSIONS_DIR)
            
            if (extensionsUrl != null) {
                val extensionFiles = loadExtensionFiles(extensionsUrl, loader)
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

    private fun loadExtensionFiles(extensionsUrl: java.net.URL, loader: ClassLoader): List<String> {
        val files = mutableListOf<String>()
        try {
            val connection = extensionsUrl.openConnection()
            if (connection is java.net.JarURLConnection) {
                val jarFile = connection.jarFile
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
                // Try to load from directory
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
                    // Fallback: try to load known extension files directly from classpath
                    val knownExtensions = listOf(
                        "swagger", "swagger3", "jackson", "gson", "spring", 
                        "spring-validations", "spring-webflux", "spring-configuration", "spring-properties",
                        "module", "ignore", "deprecated", "jakarta-validation", "javax-validation", "converts",
                        "field-utils",
                        "field-order-alphabetically", "field-order-alphabetically-desc",
                        "field-order-child-first", "field-order-parent-first",
                        "jakarta-validation-strict", "javax-validation-strict"
                    )
                    for (extName in knownExtensions) {
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
