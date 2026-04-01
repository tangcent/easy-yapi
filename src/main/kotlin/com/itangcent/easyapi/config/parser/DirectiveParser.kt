package com.itangcent.easyapi.config.parser

import com.itangcent.easyapi.settings.Settings

/**
 * Parses and processes directive lines in configuration files.
 *
 * Directives control parsing behavior and conditional inclusion:
 * - `###set` - Set parsing options
 * - `###if` - Conditional inclusion
 * - `###endif` - End conditional block
 *
 * ## Supported Set Options
 * | Option | Values | Description |
 * |--------|--------|-------------|
 * | resolveProperty | true/false | Enable property resolution |
 * | resolveMulti | FIRST/LAST/LONGEST/SHORTEST/ERROR | Multi-value resolution mode |
 * | ignoreNotFoundFile | true/false | Ignore missing included files |
 * | ignoreUnresolved | true/false | Ignore unresolved properties |
 *
 * ## Conditional Expressions
 * ```
 * ###if builtInConfig==true
 * ###if httpClient!=curl
 * ```
 *
 * @param state The directive state to modify
 * @param settings Settings for evaluating conditions
 */
class DirectiveParser(
    private val state: DirectiveState,
    private val settings: Settings?
) {
    fun handle(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.startsWith("###")) return false

        when {
            trimmed.startsWith("###set") -> {
                val expr = trimmed.removePrefix("###set").trim()
                applySet(expr)
            }
            trimmed.startsWith("###if") -> {
                val expr = trimmed.removePrefix("###if").trim()
                state.pushCondition(evaluateIf(expr))
            }
            trimmed.startsWith("###endif") -> state.popCondition()
        }
        return true
    }

    private fun applySet(expr: String) {
        val (key, value) = splitKeyValue(expr) ?: return
        when (key) {
            "resolveProperty" -> state.resolveProperty = value.equals("true", true)
            "resolveMulti" -> state.resolveMulti = runCatching {
                ResolveMultiMode.valueOf(value.uppercase())
            }.getOrDefault(ResolveMultiMode.FIRST)
            "ignoreNotFoundFile" -> state.ignoreNotFoundFile = value.equals("true", true)
            "ignoreUnresolved" -> state.ignoreUnresolved = value.equals("true", true)
        }
    }

    private fun evaluateIf(expr: String): Boolean {
        val e = expr.trim()
        if (e.isEmpty()) return false

        val ops = listOf("!=", "==", "=")
        val op = ops.firstOrNull { e.contains(it) } ?: return false
        val parts = e.split(op, limit = 2).map { it.trim() }
        if (parts.size != 2) return false
        val key = parts[0]
        val expected = parts[1].trim('"', '\'')
        val actual = getSettingValue(key)
        return when (op) {
            "!=" -> actual != expected
            else -> actual == expected
        }
    }

    private fun getSettingValue(key: String): String? {
        val s = settings ?: return null
        return when (key) {
            "builtInConfig" -> s.builtInConfig
            "remoteConfig" -> s.remoteConfig.joinToString("\n")
            "recommendConfig" -> s.recommendConfigs
            "logLevel" -> s.logLevel.toString()
            "httpTimeOut" -> s.httpTimeOut.toString()
            "unsafeSsl" -> s.unsafeSsl.toString()
            "httpClient" -> s.httpClient
            "postmanToken" -> s.postmanToken
            "feignEnable" -> s.feignEnable.toString()
            "jaxrsEnable" -> s.jaxrsEnable.toString()
            "actuatorEnable" -> s.actuatorEnable.toString()
            else -> null
        }
    }

    private fun splitKeyValue(expr: String): Pair<String, String>? {
        val idx = expr.indexOf('=')
        if (idx <= 0) return null
        val key = expr.substring(0, idx).trim()
        val value = expr.substring(idx + 1).trim()
        return if (key.isEmpty()) null else key to value
    }
}
