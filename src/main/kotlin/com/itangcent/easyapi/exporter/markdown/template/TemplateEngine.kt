package com.itangcent.easyapi.exporter.markdown.template

import com.itangcent.easyapi.exporter.markdown.MarkdownEscapeUtils
import com.itangcent.easyapi.logging.IdeaLog

/**
 * Hand-rolled, dependency-free interpreter for the `{{ }}` templating language
 * (CONTRACT § Templating language; design.md § Templating Language — Implementation Spec).
 *
 * Pipeline: **lex** → preprocess standalone-line trimming + trim markers → **parse** to AST →
 * **evaluate** against a [TemplateModel] + [RenderContext] with a loop-scope stack.
 *
 * ## Semantics
 *
 * - `{{x}}` interpolates [x] with [MarkdownEscapeUtils.escape]; `{{{x}}}` interpolates raw.
 * - `{{#if expr}}…{{else}}…{{/if}}` truthiness (CONTRACT § Truthiness: null/""/empty-list/
 *   `false`/`0` falsy).
 * - `{{#each list as item}}…{{/each}}` iterates; inside, `{{@index}}`/`{{@first}}`/`{{@last}}`
 *   are loop-scoped specials. Without an `as` clause, the current item is the implicit
 *   context (its fields are reachable as bare paths).
 * - `{{!-- comment --}}` is ignored.
 * - `{{helperName arg1 arg2}}` is a helper call (resolved via [TemplateHelpers]; unknown → empty).
 * - `{{date(pattern)}}` / `{{meta.date(pattern)}}` is a built-in call (resolved via
 *   [TemplateBuiltins]). `{{date}}` (no parens) falls through to the built-in registry when
 *   the path doesn't resolve to a model/loop variable.
 * - **Whitespace rules**: a line containing only block tags + whitespace has its entire line
 *   + trailing newline removed (standalone-line trimming). `{{-` / `-}}` trim
 *   whitespace/newlines immediately before/after a tag.
 *
 * **Pure**: no PSI/VFS access (NFR-4). The only side effect is `IdeaLog.info`/`warn` on
 * unresolved paths and built-in failures .
 */
object TemplateEngine : IdeaLog {

    /**
     * Renders [template] against [model] + [ctx].
     *
     * On parse failure: throws [TemplateParseException] (the orchestrator catches it and
     * falls back to the default template — Req 5.1).
     * On execution failure: throws [TemplateRenderException] (same fallback — Req 5.2).
     * Missing variable/helper: empty + `info` log, no throw .
     */
    fun render(template: String, model: TemplateModel, ctx: RenderContext): String {
        val preprocessed = preprocessStandaloneLines(template)
        val tokens = lex(preprocessed)
        val trimmed = applyTrimMarkers(tokens)
        val ast = parse(trimmed)
        return Evaluator(model, ctx).render(ast)
    }

    // ------------------------------------------------------------------
    // Preprocessing: standalone-line trimming
    // ------------------------------------------------------------------

    /** Matches a single block-tag token (start, else, or end of `{{#if}}`/`{{#each}}`). */
    private val blockTagRegex = Regex(
        """\{\{#(?:if|each)\b[^}]*\}\}|\{\{/(?:if|each)\}\}|\{\{else\}\}""",
    )

    /**
     * Removes lines whose only non-whitespace content is block tags: keeps the tags themselves
     * (concatenated, no surrounding whitespace) and drops the trailing newline.
     *
     * Example: `"a\n  {{#if x}}  \nb\n  {{/if}}  \nc"` → `"a\n{{#if x}}b\n{{/if}}c"`.
     */
    private fun preprocessStandaloneLines(template: String): String {
        val lines = template.split('\n')
        val out = StringBuilder()
        for ((idx, line) in lines.withIndex()) {
            val hasBlockTag = blockTagRegex.containsMatchIn(line)
            val stripped = blockTagRegex.replace(line, "")
            if (hasBlockTag && stripped.isBlank()) {
                // Standalone line (contains block tags + only whitespace) — keep only the block
                // tags, no surrounding whitespace, no trailing newline.
                for (match in blockTagRegex.findAll(line)) out.append(match.value)
            } else {
                // Regular line (including blank lines) — keep content + trailing newline.
                out.append(line)
                if (idx < lines.size - 1) out.append('\n')
            }
        }
        return out.toString()
    }

    // ------------------------------------------------------------------
    // Lexer
    // ------------------------------------------------------------------

    private sealed class Token {
        class Text(val raw: String) : Token()
        class Tag(val content: String, val raw: Boolean, val trimLeft: Boolean, val trimRight: Boolean) : Token()
        class Comment(val content: String) : Token()
    }

    /** Splits the template into [Token]s. */
    private fun lex(template: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val text = StringBuilder()
        var i = 0
        while (i < template.length) {
            if (i + 1 < template.length && template[i] == '{' && template[i + 1] == '{') {
                // Detect the form: {{{ raw }}}, {{!-- comment --}}, or {{ tag }}
                val isRaw = i + 2 < template.length && template[i + 2] == '{'
                val isComment = i + 4 < template.length &&
                    template[i + 2] == '!' && template[i + 3] == '-' && template[i + 4] == '-'
                if (isRaw) {
                    flushText(tokens, text)
                    val end = template.indexOf("}}}", i + 3)
                    if (end == -1) {
                        // Unclosed — treat the rest as text.
                        text.append(template.substring(i))
                        i = template.length
                    } else {
                        tokens.add(Token.Tag(template.substring(i + 3, end).trim(), raw = true, trimLeft = false, trimRight = false))
                        i = end + 3
                    }
                } else if (isComment) {
                    flushText(tokens, text)
                    val end = template.indexOf("--}}", i + 5)
                    if (end == -1) {
                        // Unclosed comment — drop the rest.
                        i = template.length
                    } else {
                        tokens.add(Token.Comment(template.substring(i + 5, end)))
                        i = end + 4
                    }
                } else {
                    flushText(tokens, text)
                    var j = i + 2
                    var trimLeft = false
                    if (j < template.length && template[j] == '-') {
                        trimLeft = true
                        j++
                    }
                    val end = template.indexOf("}}", j)
                    if (end == -1) {
                        // Unclosed tag — emit the original `{{` as text.
                        text.append("{{")
                        i = j
                    } else {
                        var content = template.substring(j, end)
                        var trimRight = false
                        if (content.endsWith('-')) {
                            trimRight = true
                            content = content.dropLast(1)
                        }
                        tokens.add(Token.Tag(content.trim(), raw = false, trimLeft = trimLeft, trimRight = trimRight))
                        i = end + 2
                    }
                }
            } else {
                text.append(template[i])
                i++
            }
        }
        flushText(tokens, text)
        return tokens
    }

    private fun flushText(tokens: MutableList<Token>, text: StringBuilder) {
        if (text.isNotEmpty()) {
            tokens.add(Token.Text(text.toString()))
            text.clear()
        }
    }

    /** Applies `{{-` / `-}}` trim markers by trimming adjacent whitespace on TEXT tokens. */
    private fun applyTrimMarkers(tokens: List<Token>): List<Token> {
        val result = mutableListOf<Token>()
        var pendingTrimRight = false
        for (token in tokens) {
            when (token) {
                is Token.Tag -> {
                    if (token.trimLeft && result.isNotEmpty() && result.last() is Token.Text) {
                        val prev = result.removeAt(result.size - 1) as Token.Text
                        val trimmed = prev.raw.trimEnd()
                        if (trimmed.isNotEmpty()) result.add(Token.Text(trimmed))
                    }
                    result.add(token)
                    pendingTrimRight = token.trimRight
                }
                is Token.Text -> {
                    if (pendingTrimRight) {
                        val trimmed = token.raw.trimStart()
                        if (trimmed.isNotEmpty()) result.add(Token.Text(trimmed))
                        pendingTrimRight = false
                    } else {
                        result.add(token)
                    }
                }
                is Token.Comment -> {
                    pendingTrimRight = false
                    result.add(token)
                }
            }
        }
        return result
    }

    // ------------------------------------------------------------------
    // Parser
    // ------------------------------------------------------------------

    private sealed class Node {
        class Text(val raw: String) : Node()
        class Interpolate(val expr: Expr, val raw: Boolean) : Node()
        class IfBlock(val condition: Expr, val thenBranch: List<Node>, val elseBranch: List<Node>?) : Node()
        class EachBlock(val collection: Expr, val alias: String?, val body: List<Node>) : Node()
        object Comment : Node()
    }

    private sealed class Expr {
        class Path(val segments: List<String>) : Expr()
        class Helper(val name: String, val args: List<Expr>) : Expr()
        class BuiltinCall(val name: String, val rawArg: String?) : Expr()
        class Literal(val value: Any) : Expr()
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun parseNodes(): List<Node> {
            val nodes = mutableListOf<Node>()
            while (pos < tokens.size) {
                val token = tokens[pos]
                if (token is Token.Tag) {
                    val c = token.content
                    if (c == "else" || c.startsWith("/")) break // stop tag — caller consumes
                }
                val node = parseNode() ?: break
                nodes.add(node)
            }
            return nodes
        }

        private fun parseNode(): Node? {
            val token = tokens.getOrNull(pos) ?: return null
            return when (token) {
                is Token.Text -> { pos++; Node.Text(token.raw) }
                is Token.Comment -> { pos++; Node.Comment }
                is Token.Tag -> {
                    val c = token.content
                    when {
                        c == "else" || c.startsWith("/") -> null // stop tag
                        c.startsWith("#if ") -> {
                            pos++
                            val condition = parseExpr(c.substring(4))
                            val thenBranch = parseNodes()
                            var elseBranch: List<Node>? = null
                            val next = tokens.getOrNull(pos)
                            if (next is Token.Tag && next.content == "else") {
                                pos++
                                elseBranch = parseNodes()
                            }
                            expectEndTag("/if")
                            Node.IfBlock(condition, thenBranch, elseBranch)
                        }
                        c.startsWith("#each ") -> {
                            pos++
                            val (expr, alias) = parseEachHeader(c.substring(6))
                            val body = parseNodes()
                            expectEndTag("/each")
                            Node.EachBlock(expr, alias, body)
                        }
                        else -> {
                            pos++
                            Node.Interpolate(parseExpr(c), raw = token.raw)
                        }
                    }
                }
            }
        }

        private fun expectEndTag(expected: String) {
            val token = tokens.getOrNull(pos)
            if (token !is Token.Tag || token.content != expected) {
                throw TemplateParseException("Expected '$expected' at token $pos, got: $token")
            }
            pos++
        }

        private fun parseEachHeader(rest: String): Pair<Expr, String?> {
            val asIdx = rest.indexOf(" as ")
            if (asIdx == -1) return parseExpr(rest) to null
            val exprPart = rest.substring(0, asIdx).trim()
            val alias = rest.substring(asIdx + 4).trim()
            return parseExpr(exprPart) to alias
        }

        private fun parseExpr(content: String): Expr {
            val trimmed = content.trim()
            // Builtin call form: NAME(...) or meta.NAME(...)
            val parenIdx = trimmed.indexOf('(')
            if (parenIdx != -1 && trimmed.endsWith(")")) {
                var name = trimmed.substring(0, parenIdx).trim()
                val rawArg = trimmed.substring(parenIdx + 1, trimmed.length - 1)
                if (name.startsWith("meta.")) name = name.substring(5)
                return Expr.BuiltinCall(name, rawArg)
            }
            // Helper call form: NAME arg1 arg2 ...
            val parts = trimmed.split(' ').filter { it.isNotBlank() }
            if (parts.size >= 2) {
                val name = parts[0]
                val args = parts.drop(1).map { parseSingleExpr(it) }
                return Expr.Helper(name, args)
            }
            return parseSingleExpr(trimmed)
        }

        private fun parseSingleExpr(s: String): Expr {
            if (s.length >= 2 && s.startsWith("'") && s.endsWith("'")) {
                return Expr.Literal(s.substring(1, s.length - 1))
            }
            if (s == "true") return Expr.Literal(true)
            if (s == "false") return Expr.Literal(false)
            s.toIntOrNull()?.let { return Expr.Literal(it) }
            s.toLongOrNull()?.let { return Expr.Literal(it) }
            s.toDoubleOrNull()?.let { return Expr.Literal(it) }
            val segments = s.split('.').filter { it.isNotEmpty() }
            return Expr.Path(segments)
        }
    }

    private fun parse(tokens: List<Token>): List<Node> = Parser(tokens).parseNodes()

    // ------------------------------------------------------------------
    // Evaluator
    // ------------------------------------------------------------------

    private class Evaluator(private val model: TemplateModel, private val ctx: RenderContext) {

        private data class LoopScope(
            val alias: String?,
            val item: Any?,
            val index: Int,
            val isFirst: Boolean,
            val isLast: Boolean,
        )

        private val scopes = mutableListOf<LoopScope>()

        fun render(nodes: List<Node>): String {
            val sb = StringBuilder()
            for (node in nodes) renderNode(node, sb)
            return sb.toString()
        }

        private fun renderNode(node: Node, sb: StringBuilder) {
            when (node) {
                is Node.Text -> sb.append(node.raw)
                Node.Comment -> { /* ignored */ }
                is Node.Interpolate -> {
                    val value = evalExpr(node.expr)
                    val str = stringify(value)
                    if (node.raw) sb.append(str) else sb.append(MarkdownEscapeUtils.escape(str))
                }
                is Node.IfBlock -> {
                    val cond = evalExpr(node.condition)
                    if (isTruthy(cond)) {
                        sb.append(render(node.thenBranch))
                    } else {
                        node.elseBranch?.let { sb.append(render(it)) }
                    }
                }
                is Node.EachBlock -> {
                    val collection = evalExpr(node.collection)
                    val items = asIterableList(collection)
                    for ((i, item) in items.withIndex()) {
                        scopes.add(
                            LoopScope(
                                alias = node.alias,
                                item = item,
                                index = i,
                                isFirst = i == 0,
                                isLast = i == items.size - 1,
                            ),
                        )
                        try {
                            sb.append(render(node.body))
                        } finally {
                            scopes.removeAt(scopes.size - 1)
                        }
                    }
                }
            }
        }

        private fun evalExpr(expr: Expr): Any? = when (expr) {
            is Expr.Literal -> expr.value
            is Expr.Path -> evalPath(expr.segments)
            is Expr.BuiltinCall -> TemplateBuiltins.resolve(expr.name, ctx, expr.rawArg)
            is Expr.Helper -> evalHelper(expr.name, expr.args)
        }

        private fun evalHelper(name: String, args: List<Expr>): Any? {
            val argValues = args.map { evalExpr(it) }
            val result = TemplateHelpers.resolve(name, argValues, ctx)
            if (result == null) {
                LOG.info("Unknown helper '$name' (args=$argValues) — resolved to empty")
            }
            return result
        }

        private fun evalPath(segments: List<String>): Any? {
            if (segments.isEmpty()) return null
            val first = segments.first()

            // Loop-scoped specials: @index, @first, @last
            if (first.startsWith("@")) {
                val scope = scopes.lastOrNull() ?: return null
                return when (first.substring(1)) {
                    "index" -> scope.index
                    "first" -> scope.isFirst
                    "last" -> scope.isLast
                    else -> null
                }
            }

            // meta.* — always a built-in (CONTRACT § Reserved root)
            if (first == "meta" && segments.size >= 2) {
                return TemplateBuiltins.resolve(segments[1], ctx, arg = null)
            }

            // Loop variable (alias or implicit context)
            val fromScope = resolveFromScope(first, segments)
            if (fromScope != null) return fromScope

            // Model field
            val fromModel = walkFields(model, segments)
            if (fromModel != null) return fromModel

            // Built-in fall-through: a bare single-segment name matching a built-in (e.g. {{date}})
            if (segments.size == 1 && TemplateBuiltins.isBuiltin(first)) {
                return TemplateBuiltins.resolve(first, ctx, arg = null)
            }

            LOG.info("Unresolved path: ${segments.joinToString(".")}")
            return null
        }

        /**
         * Walks the loop-scope stack from innermost to outermost.
         *  - If an explicit alias matches [first], resolves `segments[1..]` against that item.
         *  - If a scope has no alias (implicit context), attempts to resolve `segments` against
         *    the current item — falls through to outer scopes if the field is null/missing.
         */
        private fun resolveFromScope(first: String, segments: List<String>): Any? {
            for (scope in scopes.asReversed()) {
                if (scope.alias == first) {
                    return walkFields(scope.item, segments.drop(1))
                }
                if (scope.alias == null) {
                    val candidate = walkFields(scope.item, segments)
                    if (candidate != null) return candidate
                }
            }
            return null
        }

        /** Walks a path of field accesses (with `[N]` index support) against [root]. */
        private fun walkFields(root: Any?, segments: List<String>): Any? {
            var current: Any? = root
            for (seg in segments) {
                current = getField(current, seg) ?: return null
            }
            return current
        }

        /**
         * Resolves [name] against [target]. Supports `[N]` indexing by splitting the segment
         * into a field name + an index — e.g. `rows[0]` → field `rows`, index `0`.
         */
        private fun getField(target: Any?, name: String): Any? {
            if (target == null) return null
            val bracketIdx = name.indexOf('[')
            if (bracketIdx != -1) {
                val fieldName = name.substring(0, bracketIdx)
                val closeIdx = name.indexOf(']', bracketIdx)
                if (closeIdx == -1) return null
                val index = name.substring(bracketIdx + 1, closeIdx).toIntOrNull() ?: return null
                val list = getFieldByName(target, fieldName) as? List<*> ?: return null
                return list.getOrNull(index)
            }
            return getFieldByName(target, name)
        }

        private fun getFieldByName(target: Any, name: String): Any? = when (target) {
            is TemplateModel -> when (name) {
                "moduleName" -> target.moduleName
                "groups" -> target.groups
                "endpointCount" -> target.endpointCount
                else -> null
            }
            is Group -> when (name) {
                "folder" -> target.folder
                "endpoints" -> target.endpoints
                else -> null
            }
            is Endpoint -> when (name) {
                "name" -> target.name
                "description" -> target.description
                "protocol" -> target.protocol
                "path" -> target.path
                "method" -> target.method
                "http" -> target.http
                "grpc" -> target.grpc
                else -> null
            }
            is HttpView -> when (name) {
                "pathParams" -> target.pathParams
                "queryParams" -> target.queryParams
                "formParams" -> target.formParams
                "headers" -> target.headers
                "body" -> target.body
                "response" -> target.response
                "hasRequestContent" -> target.hasRequestContent
                else -> null
            }
            is GrpcView -> when (name) {
                "serviceName" -> target.serviceName
                "methodName" -> target.methodName
                "streamingType" -> target.streamingType
                "fullPath" -> target.fullPath
                "body" -> target.body
                "response" -> target.response
                else -> null
            }
            is Param -> when (name) {
                "name" -> target.name
                "defaultValue" -> target.defaultValue
                "required" -> target.required
                "type" -> target.type
                "description" -> target.description
                else -> null
            }
            is Header -> when (name) {
                "name" -> target.name
                "value" -> target.value
                "required" -> target.required
                "description" -> target.description
                else -> null
            }
            is BodyView -> when (name) {
                "rows" -> target.rows
                "demo" -> target.demo
                else -> null
            }
            is Row -> when (name) {
                "name" -> target.name
                "type" -> target.type
                "desc" -> target.desc
                else -> null
            }
            else -> null
        }

        /** CONTRACT § Truthiness — null/""/empty-list/`false`/`0` falsy, else truthy. */
        private fun isTruthy(value: Any?): Boolean = when (value) {
            null -> false
            is Boolean -> value
            is Int, is Long -> (value as Number).toLong() != 0L
            is Double, is Float -> (value as Number).toDouble() != 0.0
            is String -> value.isNotEmpty()
            is List<*> -> value.isNotEmpty()
            else -> true
        }

        private fun asIterableList(value: Any?): List<*> = when (value) {
            is List<*> -> value
            null -> emptyList<Any?>()
            else -> throw TemplateRenderException(
                "#each requires a list, got ${value::class.simpleName}: ${value.toString().take(50)}"
            )
        }

        private fun stringify(value: Any?): String = when (value) {
            null -> ""
            is String -> value
            is Boolean -> value.toString()
            is Number -> value.toString()
            else -> value.toString()
        }
    }
}

/** Raised when the template cannot be parsed (unclosed block, bad syntax). Req 5.1. */
class TemplateParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Raised when the template fails at execution time (type error mid-render). Req 5.2. */
class TemplateRenderException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
