package com.itangcent.easyapi.util

data class PathVariablePattern(
    val name: String,
    val possibleValues: List<String>,
    val defaultValue: String
) {
    companion object {
        private val PATH_VARIABLE_PATTERN = Regex("\\{([^}]+)}")

        fun parsePathVariable(pattern: String): PathVariablePattern? {
            val colonIndex = pattern.indexOf(':')
            return if (colonIndex >= 0) {
                val name = pattern.substring(0, colonIndex).trim()
                val valuesPart = pattern.substring(colonIndex + 1).trim()
                val possibleValues = valuesPart.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                val default = possibleValues.firstOrNull() ?: ""
                PathVariablePattern(name, possibleValues, default)
            } else {
                null
            }
        }

        fun extractPathVariablesFromPath(path: String): List<PathVariablePattern> {
            val results = mutableListOf<PathVariablePattern>()
            PATH_VARIABLE_PATTERN.findAll(path).forEach { matchResult ->
                val pattern = matchResult.groupValues[1]
                parsePathVariable(pattern)?.let { results.add(it) }
            }
            return results
        }

        fun normalizePath(path: String): String {
            return PATH_VARIABLE_PATTERN.replace(path) { matchResult ->
                val pattern = matchResult.groupValues[1]
                val parsed = parsePathVariable(pattern)
                if (parsed != null) {
                    "{${parsed.name}}"
                } else {
                    matchResult.value
                }
            }
        }
    }
}
