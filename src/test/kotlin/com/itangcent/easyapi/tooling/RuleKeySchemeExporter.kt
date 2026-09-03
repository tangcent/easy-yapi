package com.itangcent.easyapi.tooling

import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.RuleKeyCatalog
import java.io.File

/**
 * Builds a machine-readable catalog of every rule key's self-describing
 * [com.itangcent.easyapi.core.rule.RuleKeyScheme].
 *
 * This is the **automated** counterpart to the in-plugin
 * [com.itangcent.easyapi.core.ai.tools.ListRuleKeysTool] (which enumerates the
 * same keys at runtime via [com.itangcent.easyapi.core.rule.RuleKeyRegistry]) —
 * the external `easy-yapi-assistant` skill needs an equivalent static catalog it
 * can ship without running IntelliJ.
 *
 * The key sources are enumerated from the single, shared
 * [RuleKeyCatalog.SOURCES] list (runtime and build-time view parity):
 * - [com.itangcent.easyapi.core.rule.RuleKeys] — general/shared keys (`source = "general"`)
 * - [com.itangcent.easyapi.channel.postman.PostmanRuleKeys],
 *   [com.itangcent.easyapi.channel.hoppscotch.HoppscotchRuleKeys],
 *   [com.itangcent.easyapi.channel.openapi.OpenApiRuleKeys] — channel keys
 *   (`source = <channel id>`)
 * - [com.itangcent.easyapi.framework.custom.CustomRuleKeys] — framework keys
 *   (`source = RuleKeyCatalog.FRAMEWORK_NAME`)
 * - [com.itangcent.easyapi.core.rule.ImplicitConfigKeys] — keys read by name via
 *   `ConfigReader.getFirst(...)` (`source = "implicit"`)
 *
 * Because each source is enumerated via [RuleKey.collectFrom] (reflection over
 * the object's `RuleKey` properties), **any new key added to an existing
 * `*RuleKeys` object is picked up automatically** when the task re-runs. A
 * brand-new source object (a new channel/framework) must be registered in
 * [RuleKeyCatalog.SOURCES] — a consistency test
 * ([com.itangcent.easyapi.tooling.RuleKeySchemeExporterTest]) fails if a
 * registered [com.itangcent.easyapi.core.rule.RuleKeyRegistry] source is
 * missing from that list.
 *
 * This is a **build-time tool only** — it lives in the test source set and is
 * not shipped in the plugin JAR.
 *
 * Outputs:
 * - **JSON** (`rule-keys.json`) — the authoritative machine-readable catalog of
 *   every key with its full scheme: `{name, aliases, type, source, mode, contextKinds,
 *   outputShape, additionalBindings, summary, staticConfiguration, dryRunnable, jsonValue,
 *   notes}`. Each `additionalBindings` entry is `{name, kind}` where `kind` is an
 *   object id or a context-kind id (e.g. `"field"`) for a context injection.
 * - **Markdown** (`rule-keys.md`) — a human-readable rendering of the same
 *   catalog, derived from the JSON.
 *
 * Run via `./gradlew syncRuleKeySchemes` (see `build.gradle.kts`).
 */
object RuleKeySchemeExporter {

    /**
     * Collects every exported key from [RuleKeyCatalog.SOURCES], de-duplicated
     * by primary name (mirrors [com.itangcent.easyapi.core.rule.RuleKeyRegistry.assembleKeys]
     * precedence: earlier sources win).
     *
     * Returns the shared [RuleKeyCatalog.SchemeEntry] view (spec D4.3 parity):
     * the in-plugin `list_rule_keys` tool and this exporter emit the *same*
     * entry type, so their field sets are identical by construction.
     */
    fun collectKeys(): List<com.itangcent.easyapi.core.rule.RuleKeyCatalog.SchemeEntry> =
        com.itangcent.easyapi.core.rule.RuleKeyCatalog.schemeEntries(
            RuleKeyCatalog.assembledKeyInfos().map { (key, source) ->
                com.itangcent.easyapi.core.rule.RuleKeyCatalog.RuleKeyInfo(key, source)
            }
        )

    /**
     * Renders [keys] as a JSON document. Pure so both the gradle task and the
     * test can invoke it; serialization keeps primitives inline (gson).
     */
    fun toJson(keys: List<com.itangcent.easyapi.core.rule.RuleKeyCatalog.SchemeEntry>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        keys.forEachIndexed { index, k ->
            sb.append("  {\n")
            sb.append("    \"name\": \"${esc(k.name)}\",\n")
            sb.append("    \"aliases\": [${k.aliases.joinToString(", ") { "\"${esc(it)}\"" }}],\n")
            sb.append("    \"type\": \"${esc(k.type)}\",\n")
            sb.append("    \"source\": \"${esc(k.source)}\",\n")
            sb.append("    \"mode\": \"${esc(k.mode)}\",\n")
            sb.append("    \"contextKinds\": [${k.contextKinds.joinToString(", ") { "\"${esc(it)}\"" }}],\n")
            sb.append("    \"outputShape\": ${k.outputShape?.let { "\"${esc(it)}\"" } ?: "null"},\n")
            sb.append("    \"additionalBindings\": [${k.additionalBindings.joinToString(", ") { bindingJson(it) }}],\n")
            sb.append("    \"summary\": \"${esc(k.summary ?: "(no summary — legacy key without a self-describing scheme)")}\",\n")
            sb.append("    \"staticConfiguration\": ${k.staticConfiguration},\n")
            sb.append("    \"dryRunnable\": ${k.dryRunnable},\n")
            sb.append("    \"jsonValue\": ${k.jsonValue},\n")
            sb.append("    \"notes\": [${k.notes.joinToString(", ") { "\"${esc(it)}\"" }}]\n")
            sb.append("  }${if (index < keys.lastIndex) "," else ""}\n")
        }
        sb.append("]\n")
        return sb.toString()
    }

    /**
     * Renders [keys] as a human-readable Markdown catalog. Groups by [source]
     * so channel/framework/implicit keys stay discoverable.
     */
    fun toMarkdown(keys: List<com.itangcent.easyapi.core.rule.RuleKeyCatalog.SchemeEntry>): String {
        val sb = StringBuilder()
        sb.append("# EasyApi Rule-Key Catalog\n\n")
        sb.append(
            "Automatically generated from every rule key's self-describing scheme " +
                "(`RuleKeySchemeExporter`). Do **not** edit by hand — run " +
                "`./gradlew syncRuleKeySchemes`.\n\n"
        )
        sb.append("**Never invent keys not listed here** — unknown keys are silently ignored.\n\n")
        sb.append("## Legend\n\n")
        sb.append("- **Type** — `StringKey` / `BooleanKey` / `EventKey` / `IntKey`\n")
        sb.append("- **Mode** — `SINGLE` (replace) / `MERGE` / `MERGE_DISTINCT` / `ANY` / `ALL` / `IGNORE_ERROR` / `THROW_IN_ERROR`\n")
        sb.append("- **Context kinds** — `empty` / `class` / `method` / `field` / `parameter`\n\n")

        keys.groupBy { it.source }.forEach { (source, grouped) ->
            sb.append("## ${sourceLabel(source)}\n\n")
            sb.append("| Key | Type | Mode | Context | Aliases | Summary |\n")
            sb.append("|-----|------|------|---------|---------|---------|\n")
            grouped.forEach { k ->
                val modeCol = if (k.source == "implicit") "—" else "`${k.mode}`"
                val ctxCol = k.contextKinds.ifEmpty { listOf("—") }.joinToString("/") { "`$it`" }
                val aliasCol = k.aliases.ifEmpty { listOf("") }.joinToString(", ") { "`$it`" }
                val summaryCol = k.summary ?: "(no summary — legacy key without a self-describing scheme)"
                sb.append("| `${k.name}` | ${k.type} | $modeCol | $ctxCol | $aliasCol | ${inlineCell(summaryCol)} |\n")
            }
            sb.append("\n")

            // Per-key notes that carry extra guidance (JSON value shape, etc.)
            grouped.filter { it.notes.isNotEmpty() }.forEach { k ->
                sb.append("> `${k.name}` notes: ${k.notes.joinToString("; ")}\n")
            }
            grouped.filter { it.jsonValue }.forEach { k ->
                sb.append("> `${k.name}` value must be a valid JSON object (single line).\n")
            }
            if (grouped.any { it.notes.isNotEmpty() } || grouped.any { it.jsonValue }) {
                sb.append("\n")
            }
        }

        sb.append("---\n\n## Common key-name mistakes (do NOT use)\n\n")
        sb.append("| Does NOT exist | Use instead |\n|----------------|-------------|\n")
        sb.append("| `api.header` | `method.additional.header` |\n")
        sb.append("| `api.header.additional` | `method.additional.header` |\n")
        sb.append("| `path.prefix` | `class.prefix.path` / `endpoint.prefix.path` |\n")
        return sb.toString()
    }

    private fun sourceLabel(source: String): String = when (source) {
        "general" -> "General rules"
        "implicit" -> "Implicit config keys (read by name, no RuleKey constant)"
        "postman" -> "Postman rules"
        "hoppscotch" -> "Hoppscotch rules"
        "openapi" -> "OpenAPI rules"
        RuleKeyCatalog.FRAMEWORK_NAME -> "Custom Framework rules"
        else -> "$source rules"
    }

    private fun inlineCell(text: String): String =
        text.replace("|", "\\|").replace("\n", " ").trim()

    /** Renders a [RuleBinding] as `{"name":…,"kind":…}` — `kind` is the injected
     *  entity id (a script-object id, or a context-kind id for a context injection). */
    private fun bindingJson(b: com.itangcent.easyapi.core.rule.RuleBinding): String =
        "{\"name\":\"${esc(b.name)}\",\"kind\":\"${esc(b.kind)}\"}"

    private fun esc(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")

    /**
     * Entry point for the `syncRuleKeySchemes` gradle task. Writes the JSON and
     * Markdown catalogs into `args[0]` (a directory).
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val outDir = if (args.isNotEmpty()) File(args[0]) else File(".")
        outDir.mkdirs()
        val keys = collectKeys()
        File(outDir, "rule-keys.json").writeText(toJson(keys), Charsets.UTF_8)
        File(outDir, "rule-keys.md").writeText(toMarkdown(keys), Charsets.UTF_8)
        println("Exported ${keys.size} rule keys to ${outDir.absolutePath}")
    }
}