package com.itangcent.easyapi.tooling

import com.itangcent.easyapi.channel.markdown.template.BundledLanguageTemplates
import com.itangcent.easyapi.core.ai.AIService
import com.itangcent.easyapi.core.ai.AiChatRequest
import com.itangcent.easyapi.core.ai.AiChatResponse
import com.itangcent.easyapi.core.ai.tools.AiTool
import com.itangcent.easyapi.core.ai.tools.ToolKind
import com.itangcent.easyapi.core.ai.tools.ToolRegistry
import com.itangcent.easyapi.core.ai.tools.orchestratorToolRegistry
import com.itangcent.easyapi.core.ai.tools.standardRuleTools
import com.itangcent.easyapi.core.ai.tools.subAgentToolRegistry
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Generates the three **code-derived fact sheets** the external
 * `easy-yapi-assistant` skill ships next to its hand-written prose:
 *
 * - `tools.md` — the full AI-tool inventory (name / kind / approval / timeout /
 *   description) read from the three registries in
 *   [com.itangcent.easyapi.core.ai.tools.RuleTools], plus which tools have a
 *   bundled CLI script under `scripts/`. Before this existed the inventory lived only
 *   in Kotlin, so anything quoting it (SKILL.md prose, this repo's docs) drifted
 *   silently — see the G8 finding in `docs/developer/ai.md`.
 * - `locales.md` — the bundled Markdown-template locales, read from
 *   [BundledLanguageTemplates.availableLocales] (G10).
 * - `extensions.md` — the `plugin.xml` extension-point declarations and every
 *   registered implementation class (G13 / G5).
 *
 * All three are derived from code or from a static resource, so they cannot
 * claim something the build would disagree with. They are **build-time tools**:
 * this file lives in the test source set and is not shipped in the plugin JAR.
 *
 * Run via `./gradlew syncSkillFacts` (see `build.gradle.kts`).
 */
object SkillFactsExporter {

    /** Registry label → the [com.itangcent.easyapi.core.ai.agent.EntryPath] it serves. */
    private val REGISTRY_ENTRY_PATHS = linkedMapOf(
        "standard" to "REACTIVE",
        "orchestrator" to "TASK_LIST_PROGRAMMATIC",
        "sub-agent" to "SUB_AGENT",
    )

    /**
     * Bundled CLI script → the tool it mirrors.
     *
     * Explicit rather than parsed from the scripts' header comments: a header
     * edit must not be able to silently change (or drop) a claim in generated
     * output. [validateScriptMapping] fails the build if a script is missing
     * from this map, if a mapped script does not exist, or if a mapped tool name
     * is not in any registry.
     */
    private val SCRIPT_TO_TOOL = linkedMapOf(
        "get_detection_prompt.sh" to "get_detection_prompt",
        "get_existing_rules_for_key.sh" to "get_existing_rules_for_key",
        "get_key_context.sh" to "get_rule_context",
        "get_key_guide.sh" to "get_rule_detail",
        "read_rule_file.sh" to "read_rule_file",
    )

    /**
     * Scripts that deliberately mirror a *prompt layer* rather than an [AiTool],
     * so they must not be listed as a tool's CLI mirror.
     */
    private val SCRIPT_WITHOUT_TOOL = setOf(
        "list_detections.sh",   // mirrors SystemPromptBuilder.indexMessage("detection")
        "list_rule_files.sh",   // mirrors RuleFileResolver.listRuleFiles()
    )

    // ── tools.md ────────────────────────────────────────────────────────────

    /**
     * Every tool across the three registries, with the registry labels it appears in.
     *
     * Keyed by **(name, implementation class)**, not by name alone: the Reactive
     * path and the orchestrator both expose a tool called `propose_rule_content`
     * with different behaviour ([com.itangcent.easyapi.core.ai.tools.ProposeRuleContentTool]
     * vs `OrchestratorProposeRuleContentTool`). Keying by name would silently drop
     * one of them and print the other's description.
     */
    fun collectTools(): List<ToolFact> {
        val byKey = LinkedHashMap<String, MutableList<String>>()
        val tools = LinkedHashMap<String, AiTool>()

        fun record(label: String, list: List<AiTool>) {
            list.forEach { tool ->
                val key = toolKey(tool)
                tools.putIfAbsent(key, tool)
                byKey.getOrPut(key) { mutableListOf() }.add(label)
            }
        }

        record("standard", standardRuleTools())
        val subAgentTools = subAgentToolRegistry()
        record("sub-agent", subAgentTools)
        record("orchestrator", orchestratorToolRegistry(UnusedAiService, ToolRegistry(subAgentTools)))

        return tools.entries
            .sortedWith(compareBy({ it.value.name }, { it.key }))
            .map { (key, tool) ->
                ToolFact(
                    tool = tool,
                    registries = byKey.getValue(key).distinct()
                        .sortedBy { REGISTRY_ENTRY_PATHS.keys.indexOf(it) },
                )
            }
    }

    private fun toolKey(tool: AiTool): String =
        "${tool.name}|${tool::class.qualifiedName ?: tool::class.java.name}"

    fun toolsMarkdown(scriptsDir: File, tools: List<ToolFact> = collectTools()): String {
        validateScriptMapping(scriptsDir, tools)

        val sb = StringBuilder()
        sb.append("# EasyApi AI Tools — inventory\n\n")
        sb.append(
            "Auto-generated from the tool registries in " +
                "`core/ai/tools/RuleTools.kt` (`SkillFactsExporter`). Do **not** edit " +
                "by hand — run `./gradlew syncSkillFacts`.\n\n"
        )
        sb.append(
            "**Kind** — `PERCEPTION` is read-only and runs automatically; `ACTION` " +
                "changes state. **Approval** — whether the call goes through the user " +
                "approval gate.\n\n"
        )

        sb.append("## Registries\n\n")
        sb.append("| Registry | Entry path | Tools |\n")
        sb.append("|----------|------------|-------|\n")
        REGISTRY_ENTRY_PATHS.forEach { (label, entryPath) ->
            val count = tools.count { label in it.registries }
            sb.append("| `${registryFactory(label)}` | `${entryPath}` | $count |\n")
        }
        sb.append("\n")

        sb.append("## Tools (${tools.size})\n\n")
        tools.forEach { fact ->
            val t = fact.tool
            sb.append("### `${t.name}`\n\n")
            sb.append("- **Kind:** ${t.kind}")
            if (t.kind == ToolKind.ACTION) {
                sb.append(if (t.requiresApproval) " · **Approval:** required" else " · **Approval:** not required")
            }
            sb.append(" · **Timeout:** ${formatTimeout(t)}")
            sb.append(" · **Registries:** ${fact.registries.joinToString(", ") { "`$it`" }}\n")
            sb.append("- **Impl:** `${t::class.qualifiedName ?: t::class.java.name}`\n")
            sb.append("- ${inline(t.description)}\n")
            val script = SCRIPT_TO_TOOL.entries.firstOrNull { it.value == t.name }?.key
            sb.append("- **CLI mirror:** ${script?.let { "`scripts/$it`" } ?: "—"}\n\n")
        }

        sb.append("## CLI mirrors\n\n")
        sb.append("| Script | Mirrors |\n|--------|---------|\n")
        SCRIPT_TO_TOOL.forEach { (script, tool) -> sb.append("| `scripts/$script` | `${tool}` |\n") }
        sb.append("\n")
        sb.append(
            "Scripts without a tool counterpart (they mirror a prompt layer, not a " +
                "tool): ${SCRIPT_WITHOUT_TOOL.sorted().joinToString(", ") { "`$it`" }}.\n\n"
        )

        return sb.toString()
    }

    // ── locales.md ──────────────────────────────────────────────────────────

    fun localesMarkdown(): String {
        val locales = BundledLanguageTemplates.availableLocales()
        // `en` is served by the default template and is deliberately NOT a
        // registry entry (BundledLanguageTemplates.templateFor("en") == null).
        val registryLocales = locales.filter { it != "en" }
        val sb = StringBuilder()
        sb.append("# Bundled Markdown-template locales\n\n")
        sb.append(
            "Auto-generated from `BundledLanguageTemplates.availableLocales()` " +
                "(`SkillFactsExporter`). Do **not** edit by hand — run " +
                "`./gradlew syncSkillFacts`.\n\n"
        )
        sb.append("Selected with `markdown.template.language=<tag>`.\n\n")
        sb.append("| | |\n|---|---|\n")
        sb.append("| Selectable locales | ${locales.size} |\n")
        sb.append("| Bundled templates | ${registryLocales.size} |\n")
        sb.append("| Templates directory | `src/main/resources/markdown/templates/` |\n\n")
        sb.append("Bundled locale tags: ${registryLocales.joinToString(", ") { "`$it`" }}.\n\n")
        sb.append(
            "`en` is selectable but **not** in the registry — it uses the built-in " +
                "default template. Locale fallback (exact → language subtag → first " +
                "`lang-*` sibling) is implemented by `BundledLanguageTemplates` and " +
                "documented in `rule-guide.md`.\n\n"
        )
        return sb.toString()
    }

    // ── extensions.md ───────────────────────────────────────────────────────

    fun extensionsMarkdown(pluginXml: File): String {
        require(pluginXml.isFile) { "plugin.xml not found: ${pluginXml.absolutePath}" }
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(pluginXml)

        val pluginId = doc.getElementsByTagName("id").item(0)?.textContent?.trim().orEmpty()

        data class EpDeclaration(val name: String, val interfaceFqn: String, val area: String?, val dynamic: Boolean)
        val declarations = mutableListOf<EpDeclaration>()
        val epNodes = doc.getElementsByTagName("extensionPoints")
        for (i in 0 until epNodes.length) {
            val children = epNodes.item(i).childNodes
            for (j in 0 until children.length) {
                val node = children.item(j)
                if (node.nodeName != "extensionPoint") continue
                val attrs = node.attributes ?: continue
                declarations += EpDeclaration(
                    name = attrs.getNamedItem("name")?.nodeValue.orEmpty(),
                    interfaceFqn = attrs.getNamedItem("interface")?.nodeValue.orEmpty(),
                    area = attrs.getNamedItem("area")?.nodeValue,
                    dynamic = attrs.getNamedItem("dynamic")?.nodeValue == "true",
                )
            }
        }

        val implementations = linkedMapOf<String, MutableList<String>>()
        declarations.forEach { implementations[it.name] = mutableListOf() }
        val extensionBlocks = doc.getElementsByTagName("extensions")
        for (i in 0 until extensionBlocks.length) {
            val block = extensionBlocks.item(i)
            val ns = block.attributes?.getNamedItem("defaultExtensionNs")?.nodeValue
            if (ns != pluginId) continue
            val children = block.childNodes
            for (j in 0 until children.length) {
                val node = children.item(j)
                val impl = node.attributes?.getNamedItem("implementation")?.nodeValue ?: continue
                implementations.getOrPut(node.nodeName) { mutableListOf() }.add(impl)
            }
        }

        val sb = StringBuilder()
        sb.append("# EasyApi extension points & registered implementations\n\n")
        sb.append(
            "Auto-generated from `src/main/resources/META-INF/plugin.xml` " +
                "(`SkillFactsExporter`). Do **not** edit by hand — run " +
                "`./gradlew syncSkillFacts`. Adding an extension means an " +
                "`<extensionPoints>` declaration **plus** an implementation entry — " +
                "this file is the full wiring overview.\n\n"
        )
        sb.append("## Extension points\n\n")
        sb.append("| EP name | Interface | Area | Dynamic | Implementations |\n")
        sb.append("|---------|-----------|------|---------|-----------------|\n")
        declarations.forEach { d ->
            sb.append(
                "| `${d.name}` | `${d.interfaceFqn}` | ${d.area?.let { "`$it`" } ?: "_(application)_"} " +
                    "| ${if (d.dynamic) "yes" else "no"} | ${implementations[d.name]?.size ?: 0} |\n"
            )
        }
        sb.append("\n## Registered implementations\n\n")
        declarations.forEach { d ->
            val impls = implementations[d.name].orEmpty()
            sb.append("### `${d.name}` (${impls.size})\n\n")
            if (impls.isEmpty()) sb.append("_none_\n\n")
            else impls.forEach { sb.append("- `${it}`\n") }
            sb.append("\n")
        }
        sb.append(
            "> Each implementation's `id` and `enabledByDefault` are **instance** " +
                "properties, and the registries that resolve them " +
                "(`ChannelRegistry` / `FieldFormatChannelRegistry` / `FrameworkRegistry`) " +
                "are project-scoped `@Service`s reading a project-area EP — so neither " +
                "value is derivable at build time. Read them off the class above; the " +
                "user's stored preference (Settings) overrides `enabledByDefault` at runtime.\n"
        )
        return sb.toString()
    }

    // ── validation & helpers ────────────────────────────────────────────────

    /**
     * Fails loudly when the script↔tool mapping no longer matches the scripts on
     * disk, so a new/renamed script forces an explicit decision instead of
     * silently disappearing from the generated inventory.
     */
    private fun validateScriptMapping(scriptsDir: File, tools: List<ToolFact>) {
        val onDisk = scriptsDir.listFiles { f -> f.isFile && f.extension == "sh" }
            ?.map { it.name }?.toSet().orEmpty()
        val unmapped = onDisk - SCRIPT_TO_TOOL.keys - SCRIPT_WITHOUT_TOOL
        check(unmapped.isEmpty()) {
            "these scripts are neither mapped to a tool nor listed in SCRIPT_WITHOUT_TOOL " +
                "(SkillFactsExporter): ${unmapped.sorted()}"
        }
        val missingFiles = SCRIPT_TO_TOOL.keys - onDisk
        check(missingFiles.isEmpty()) {
            "SCRIPT_TO_TOOL points at scripts that do not exist: ${missingFiles.sorted()}"
        }
        val knownToolNames = tools.map { it.tool.name }.toSet()
        val unknownTools = SCRIPT_TO_TOOL.values.filterNot { it in knownToolNames }
        check(unknownTools.isEmpty()) {
            "SCRIPT_TO_TOOL maps to tool names that are in no registry: $unknownTools"
        }
    }

    private fun registryFactory(label: String): String = when (label) {
        "standard" -> "standardRuleTools()"
        "orchestrator" -> "orchestratorToolRegistry(...)"
        else -> "subAgentToolRegistry()"
    }

    private fun formatTimeout(tool: AiTool): String = when {
        tool.timeoutMs <= 0L -> "no timeout"
        tool.timeoutMs == 30_000L -> "30s (default)"
        else -> "${tool.timeoutMs / 1000}s"
    }

    private fun inline(text: String): String =
        text.replace(Regex("\\s+"), " ").replace("|", "\\|").trim()

    /** One tool's generated facts. */
    data class ToolFact(val tool: AiTool, val registries: List<String>)

    /**
     * Stand-in [AIService] for building `orchestratorToolRegistry(...)` at build
     * time. The registries only *store* the service — nothing calls it — so a
     * throwing stub is enough and keeps the exporter free of provider wiring.
     */
    private object UnusedAiService : AIService {
        override suspend fun chat(request: AiChatRequest): AiChatResponse =
            error("build-time exporter — AIService.chat() is never called")

        override suspend fun testConnection(): Result<String> =
            Result.failure(UnsupportedOperationException("build-time exporter"))
    }

    /** Entry point for the `syncSkillFacts` gradle task. */
    @JvmStatic
    fun main(args: Array<String>) {
        val outDir = if (args.isNotEmpty()) File(args[0]) else File(".")
        val repoRoot = File(System.getProperty("user.dir"))
        val scriptsDir = File(outDir, "scripts").takeIf { it.isDirectory }
            ?: repoRoot.resolve("skills/easy-yapi-assistant/scripts")
        outDir.mkdirs()

        File(outDir, "tools.md").writeText(toolsMarkdown(scriptsDir), Charsets.UTF_8)
        File(outDir, "locales.md").writeText(localesMarkdown(), Charsets.UTF_8)
        File(outDir, "extensions.md").writeText(
            extensionsMarkdown(repoRoot.resolve("src/main/resources/META-INF/plugin.xml")),
            Charsets.UTF_8
        )
        println("Exported tools.md / locales.md / extensions.md to ${outDir.absolutePath}")
    }
}
