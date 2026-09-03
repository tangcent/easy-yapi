package com.itangcent.easyapi.tooling

import com.itangcent.easyapi.core.rule.RuleKeyCatalog
import com.itangcent.easyapi.core.rule.context.RuleKeyScriptProfiler
import com.itangcent.easyapi.core.rule.context.RuleScriptProfile
import java.io.File

/**
 * Exports the **script-context** of every rule key — the same information the
 * in-plugin [com.itangcent.easyapi.core.ai.tools.GetRuleContextTool] returns to
 * the built-in agent at runtime via `RuleKeyScriptProfiler.describe(...)`:
 * execution mode, per-key `it` context kinds, available bindings, and the
 * callable script-object method signatures (request/response/api/endpoint/…
 * wrappers, reflected from the runtime classes).
 *
 * This is the automated counterpart to `get_rule_context`: the external
 * `easy-yapi-assistant` skill ships a static copy (mirroring the internal agent's
 * other surface) so it can author Groovy/Postman scripts against the real,
 * reflected object API instead of a hand-written summary.
 *
 * Uses the same [RuleKeyCatalog.assembledKeyInfos] assembly as the scheme
 * catalog, so it covers exactly the same keys — rule keys, channel/framework
 * keys, **and the implicit keys read by name via `ConfigReader.getFirst(...)`**,
 * all through the identical single interface (mirroring
 * `RuleKeyRegistry.assembleKeys`).
 *
 * This is a **build-time tool only** — it lives in the test source set and is
 * not shipped in the plugin JAR.
 *
 * Outputs:
 * - **JSON** (`rule-contexts.json`) — `get_rule_context(key=...)` parity: a JSON
 *   array of `{key, source, executionMode, itContexts, description, bindings[...], objectRefs[...]}`.
 * - **Markdown** (`rule-contexts.md`) — one `## <key>` section per key (used by
 *   `scripts/get_key_context.sh <key>`).
 *
 * Run via `./gradlew syncRuleContexts` (see `build.gradle.kts`).
 */
object RuleContextExporter {

    /** Full script-context profile for every assembled key (same order as the
     *  scheme catalog). Pure reflection — no Project needed. */
    fun collectProfiles(): List<RuleScriptProfile> =
        RuleKeyCatalog.assembledKeyInfos().map { (key, source) ->
            RuleKeyScriptProfiler.describe(key, source)
        }

    // ── hand-rolled JSON (mirrors RuleKeySchemeExporter.toJson style: no gson
    //    dependency in the standalone JVM, fully deterministic) ──────────────

    // The full script-object method signatures are identical wherever the same
    // object `id` appears (the profiler reflects a fixed `KClass` per id), so
    // they are emitted ONCE into a top-level `objects` dictionary and each
    // stage references objects by id. This keeps byte-for-byte `get_rule_context`
    // equivalence (any key's bindings + methods reconstruct exactly) while
    // avoiding the per-key duplication that would otherwise blow the catalog up.

    fun toJson(profiles: List<RuleScriptProfile>): String {
        val dict = collectObjectDict(profiles)
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"objects\": ").append(objectDictJson(dict)).append(",\n")
        sb.append("  \"profiles\": [\n")
        profiles.forEachIndexed { index, p ->
            sb.append("    {\n")
            sb.append("      \"key\": \"${esc(p.key)}\",\n")
            sb.append("      \"aliases\": [${p.aliases.joinToString(", ") { "\"${esc(it)}\"" }}],\n")
            sb.append("      \"source\": \"${esc(p.source)}\",\n")
            sb.append("      \"executionMode\": \"${esc(p.executionMode)}\",\n")
            sb.append("      \"itContexts\": ${stringArrayJson(p.itContexts)},\n")
            sb.append("      \"description\": \"${esc(p.description)}\",\n")
            sb.append("      \"bindings\": ${bindingsJson(p.bindings)},\n")
            sb.append("      \"objectRefs\": ${objectRefsJson(p.objectRefs)},\n")
            sb.append("      \"notes\": [${p.notes.joinToString(", ") { "\"${esc(it)}\"" }}]\n")
            sb.append("    }${if (index < profiles.lastIndex) "," else ""}\n")
        }
        sb.append("  ]\n}\n")
        return sb.toString()
    }

    /** All distinct script-object APIs (deduped by `id`) across every profile. */
    private fun collectObjectDict(profiles: List<RuleScriptProfile>):
        LinkedHashMap<String, com.itangcent.easyapi.core.rule.context.ScriptObjectApi> {
        val dict = LinkedHashMap<String, com.itangcent.easyapi.core.rule.context.ScriptObjectApi>()
        profiles.forEach { p ->
            val allObjects = com.itangcent.easyapi.core.rule.context.RuleKeyScriptProfiler.collectAllObjects(p)
            allObjects.forEach { o -> dict.putIfAbsent(o.id, o) }
        }
        return dict
    }

    private fun objectDictJson(dict: Map<String, com.itangcent.easyapi.core.rule.context.ScriptObjectApi>): String {
        if (dict.isEmpty()) return "{}"
        val entries = dict.map { (id, o) ->
            "    \"${esc(id)}\": {\"type\":\"${esc(o.type)}\"," +
                "\"description\":\"${esc(o.description)}\"," +
                "\"methods\":${methodsJson(o.methods)}}"
        }
        return "{\n" + entries.joinToString(",\n") + "\n  }"
    }

    private fun stringArrayJson(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { "\"${esc(it)}\"" }

    private fun bindingsJson(bindings: List<com.itangcent.easyapi.core.rule.context.ScriptBinding>): String =
        bindings.joinToString(prefix = "[", postfix = "]") { b ->
            "{\"name\":\"${esc(b.name)}\"," +
                "\"aliases\":[${b.aliases.joinToString(", ") { "\"${esc(it)}\"" }}]," +
                "\"objectTypes\":[${b.objectTypes.joinToString(", ") { "\"${esc(it)}\"" }}]," +
                "\"availability\":\"${esc(b.availability)}\"," +
                "\"description\":\"${esc(b.description)}\"}"
        }

    private fun objectRefsJson(refs: List<String>): String =
        refs.joinToString(prefix = "[", postfix = "]") { "\"${esc(it)}\"" }

    private fun methodsJson(methods: List<com.itangcent.easyapi.core.rule.context.ScriptMethodApi>): String =
        methods.joinToString(prefix = "[", postfix = "]") { m ->
            "{\"name\":\"${esc(m.name)}\",\"returns\":\"${esc(m.returns)}\"," +
                "\"parameters\":[${
                    m.parameters.joinToString(", ") { p ->
                        "{\"name\":\"${esc(p.name)}\",\"type\":\"${esc(p.type)}\"," +
                            "\"vararg\":${p.vararg}}"
                    }
                }]}"
        }

    // ── human-readable markdown (read by get_key_context.sh) ────────────────
    // Object APIs live ONCE in the `## Script-Object API Reference` section;
    // each `## <key>` section lists only the object ids it binds. The CLI
    // `scripts/get_key_context.sh <key>` prints a key's section and then expands
    // the referenced object-API blocks from the shared reference.

    fun toMarkdown(profiles: List<RuleScriptProfile>): String {
        val dict = collectObjectDict(profiles)
        val sb = StringBuilder()
        sb.append("# EasyApi Rule Script-Context (get_rule_context mirror)\n\n")
        sb.append(
            "Auto-generated from each rule key's runtime script-context " +
                "(`RuleContextExporter`). Do **not** edit by hand — run " +
                "`./gradlew syncRuleContexts`. Fetch one key with " +
                "`scripts/get_key_context.sh <key>`.\n\n"
        )
        sb.append(
            "This is the same per-key bindings + script-object API the built-in " +
                "`get_rule_context` returns: it tells you exactly which script " +
                "objects (it/request/response/api/…) and methods are callable for " +
                "a given rule key. After choosing a value format, read the key's " +
                "section here before authoring a Groovy/Postman script.\n\n"
        )
        sb.append(
            "Format: a `## <key>` section declares that key's bindings and the " +
                "object `id`s it makes callable. Every object's full method " +
                "signatures live ONCE under `Script-Object API Reference` below — " +
                "look up the ids listed in a key's section there (or just run " +
                "`scripts/get_key_context.sh <key>`, which joins them for you).\n\n"
        )
        sb.append("## Script-Object API Reference\n\n")
        dict.forEach { (id, o) ->
            sb.append("### object: ").append(id).append("\n\n")
            sb.append("Type: `").append(o.type).append("` — ").append(o.description).append("\n\n")
            if (o.methods.isEmpty()) {
                // A standard external type (e.g. the java.util.List behind
                // `collection`) is reflected under the com.itangcent-only
                // policy, so its method list is intentionally empty — it is a
                // type anchor, not a no-API object.
                if (o.type.startsWith("com.itangcent")) {
                    sb.append("*(no public script methods)*\n\n")
                } else {
                    sb.append("*(standard `").append(o.type)
                        .append("` — no EasyApi-specific methods; use the type's own API)*\n\n")
                }
            } else {
                o.methods.forEach { m ->
                    val params = m.parameters.joinToString(", ") { "${it.name}: ${it.type}" }
                    sb.append("- `").append(m.name).append("(").append(params).append("): ").append(m.returns).append("`\n")
                }
                sb.append("\n")
            }
        }
        sb.append("\n---\n\n")
        profiles.forEach { p ->
            sb.append("## ").append(p.key).append("\n\n")
            sb.append("**Source:** `").append(p.source).append("`  \n")
            sb.append("**Execution mode:** `").append(p.executionMode).append("`\n")
            sb.append("**Bindings:** ").append(p.bindings.joinToString(", ") { "`${it.name}`" }).append("  \n")
            sb.append("**`it` context types:** ").append(p.itContexts.joinToString(", ") { "`$it`" }).append("\n")
            sb.append(p.description).append("\n\n")
            if (p.aliases.isNotEmpty()) {
                sb.append("**Aliases:** ").append(p.aliases.joinToString(", ") { "`$it`" }).append("\n\n")
            }
            if (p.notes.isNotEmpty()) {
                sb.append("**Notes:**\n")
                p.notes.forEach { sb.append("- ").append(it).append("\n") }
                sb.append("\n")
            }
            if (p.bindings.isNotEmpty()) {
                sb.append("**Bindings:**\n")
                p.bindings.forEach { b ->
                    sb.append("- `").append(b.name)
                    if (b.aliases.isNotEmpty()) sb.append("` (aliases: ").append(b.aliases.joinToString(", ") { "`$it`" }).append(")")
                    else sb.append("`")
                    sb.append(" — ").append(b.description).append(" (availability: ").append(b.availability).append(")\n")
                }
                sb.append("\n")
            }
            if (p.objectRefs.isNotEmpty()) {
                sb.append("**Object APIs:** ").append(p.objectRefs.joinToString(", ") { "`$it`" }).append("\n\n")
            }
        }
        return sb.toString()
    }

    private fun esc(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")

    /** Entry point for the `syncRuleContexts` gradle task. */
    @JvmStatic
    fun main(args: Array<String>) {
        val outDir = if (args.isNotEmpty()) File(args[0]) else File(".")
        outDir.mkdirs()
        val profiles = collectProfiles()
        File(outDir, "rule-contexts.json").writeText(toJson(profiles), Charsets.UTF_8)
        File(outDir, "rule-contexts.md").writeText(toMarkdown(profiles), Charsets.UTF_8)
        println("Exported ${profiles.size} rule contexts to ${outDir.absolutePath}")
    }
}