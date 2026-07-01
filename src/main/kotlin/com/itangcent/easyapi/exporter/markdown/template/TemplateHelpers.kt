package com.itangcent.easyapi.exporter.markdown.template

import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter

/**
 * Built-in helper functions exposed to templates as `{{helperName arg1 arg2}}`
 * (CONTRACT § Templating language — Helpers required by the example templates).
 *
 * The engine calls [resolve] for every helper invocation; an unknown name returns `null`
 * (the engine emits empty + `debug` log — Req 3.5). Returned values are stringified by the
 * engine before interpolation; Booleans are preserved as-is so `{{#if eq a b}}` truthiness
 * works (a string `"false"` would be truthy).
 *
 * **Pure**: no PSI/VFS access (NFR-4). The only side effect is `IdeaLog.info` on unknown
 * helpers and `IdeaLog.warn` on invalid arguments.
 *
 * Helpers:
 * - `requiredLabel bool` → `"YES"` when true, else `"NO"`.
 * - `eq a b` → Boolean equality (Kotlin `==`).
 * - `typeOf model` → `Single`→type, `Array`→`<item>[]`, `Object`→"object", `Map`→"map".
 * - `indent depth` → empty at 0; `&ensp;&ensp;`×depth + `&#124;─` otherwise.
 * - `fieldDesc field` → comment + options joined with `<br>`; options as `value :desc` / `value`.
 *   Mirrors `DefaultMarkdownFormatter.buildFieldDescription` (byte-parity).
 * - `jsonDemo model` → ``` ```json … ``` ``` fence around `ObjectModelJsonConverter.toJson`.
 */
object TemplateHelpers : IdeaLog {

    private const val INDENT_UNIT = "&ensp;&ensp;"
    private const val INDENT_ARROW = "&#124;─"
    private const val JSON_FENCE_OPEN = "```json\n"
    private const val JSON_FENCE_CLOSE = "\n```"

    /**
     * Resolves the helper [name] against [args] using [ctx] (currently unused — reserved for
     * future helpers that need ambient values). Returns `null` for unknown helpers.
     */
    fun resolve(name: String, args: List<Any?>, ctx: RenderContext): Any? {
        return when (name) {
            "requiredLabel" -> requiredLabel(args)
            "eq" -> eq(args)
            "typeOf" -> typeOf(args)
            "indent" -> indent(args)
            "fieldDesc" -> fieldDesc(args)
            "jsonDemo" -> jsonDemo(args)
            else -> {
                LOG.info("Unknown helper '$name' (args=$args) — resolved to empty")
                null
            }
        }
    }

    // ------------------------------------------------------------------
    // requiredLabel bool → "YES" | "NO"
    // ------------------------------------------------------------------

    private fun requiredLabel(args: List<Any?>): String {
        val value = args.firstOrNull()
        return if (value is Boolean && value) "YES" else "NO"
    }

    // ------------------------------------------------------------------
    // eq a b → Boolean (Kotlin ==)
    // ------------------------------------------------------------------

    private fun eq(args: List<Any?>): Boolean {
        if (args.size < 2) return false
        return args[0] == args[1]
    }

    // ------------------------------------------------------------------
    // typeOf model → Single→type, Array→<item>[], Object→"object", Map→"map"
    // Mirrors DefaultMarkdownFormatter.formatType.
    // ------------------------------------------------------------------

    private fun typeOf(args: List<Any?>): String {
        val model = args.firstOrNull()
        if (model !is ObjectModel) {
            if (model != null) {
                LOG.info("typeOf helper: expected ObjectModel, got ${model::class.simpleName}")
            }
            return ""
        }
        return formatType(model)
    }

    private fun formatType(model: ObjectModel): String = when (model) {
        is ObjectModel.Single -> model.type
        is ObjectModel.Array -> "${formatType(model.item)}[]"
        is ObjectModel.Object -> "object"
        is ObjectModel.MapModel -> "map"
    }

    // ------------------------------------------------------------------
    // indent depth → "" at 0; "&ensp;&ensp;"×depth + "&#124;─" otherwise
    // ------------------------------------------------------------------

    private fun indent(args: List<Any?>): String {
        val depth = args.firstOrNull() as? Int ?: return ""
        if (depth <= 0) return ""
        return INDENT_UNIT.repeat(depth) + INDENT_ARROW
    }

    // ------------------------------------------------------------------
    // fieldDesc field → comment + options joined with <br>
    // Mirrors DefaultMarkdownFormatter.buildFieldDescription (byte-parity).
    // ------------------------------------------------------------------

    private fun fieldDesc(args: List<Any?>): String {
        val field = args.firstOrNull()
        if (field !is FieldModel) {
            if (field != null) {
                LOG.info("fieldDesc helper: expected FieldModel, got ${field::class.simpleName}")
            }
            return ""
        }
        return buildFieldDescription(field)
    }

    private fun buildFieldDescription(fieldModel: FieldModel): String {
        val parts = mutableListOf<String>()
        fieldModel.comment?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        fieldModel.options?.takeIf { it.isNotEmpty() }?.let { options ->
            val optionDesc = options.joinToString("<br>") { opt ->
                if (opt.desc.isNullOrBlank()) "${opt.value}" else "${opt.value} :${opt.desc}"
            }
            parts.add(optionDesc)
        }
        return parts.joinToString("<br>")
    }

    // ------------------------------------------------------------------
    // jsonDemo model → ```json\n<json>\n```
    // ------------------------------------------------------------------

    private fun jsonDemo(args: List<Any?>): String {
        val model = args.firstOrNull()
        if (model !is ObjectModel) {
            if (model != null) {
                LOG.info("jsonDemo helper: expected ObjectModel, got ${model::class.simpleName}")
            }
            return ""
        }
        val json = ObjectModelJsonConverter.toJson(model)
        return JSON_FENCE_OPEN + json + JSON_FENCE_CLOSE
    }
}
