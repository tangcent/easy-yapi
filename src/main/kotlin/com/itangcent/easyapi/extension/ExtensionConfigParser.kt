package com.itangcent.easyapi.extension

object ExtensionConfigParser {

    fun parse(content: String): ExtensionConfig? {
        val lines = content.lines()
        if (lines.isEmpty()) return null

        var code = ""
        var description = ""
        var onClass: String? = null
        var defaultEnabled = false
        var inYamlFrontMatter = false
        var yamlEnded = false
        val configContent = StringBuilder()

        for (line in lines) {
            if (!yamlEnded) {
                if (line.trim() == "---") {
                    if (!inYamlFrontMatter) {
                        inYamlFrontMatter = true
                        continue
                    } else {
                        yamlEnded = true
                        continue
                    }
                }

                if (inYamlFrontMatter) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("code:") -> {
                            code = trimmed.removePrefix("code:").trim().removeSurrounding("\"")
                        }
                        trimmed.startsWith("description:") -> {
                            description = trimmed.removePrefix("description:").trim().removeSurrounding("\"")
                        }
                        trimmed.startsWith("on-class:") -> {
                            onClass = trimmed.removePrefix("on-class:").trim().removeSurrounding("\"")
                        }
                        trimmed.startsWith("default-enabled:") -> {
                            defaultEnabled = trimmed.removePrefix("default-enabled:").trim().toBoolean()
                        }
                    }
                }
            } else {
                configContent.append(line).append("\n")
            }
        }

        if (code.isBlank()) {
            return null
        }

        return ExtensionConfig(
            code = code,
            description = description,
            content = configContent.toString().trimEnd('\n'),
            onClass = onClass,
            defaultEnabled = defaultEnabled
        )
    }

    fun stripYamlFrontMatter(content: String): String {
        val lines = content.lines()
        var inYamlFrontMatter = false
        val result = StringBuilder()

        for (line in lines) {
            if (line.trim() == "---") {
                inYamlFrontMatter = !inYamlFrontMatter
                continue
            }
            if (inYamlFrontMatter) continue
            result.append(line).append("\n")
        }

        return result.toString().trimEnd('\n')
    }
}
