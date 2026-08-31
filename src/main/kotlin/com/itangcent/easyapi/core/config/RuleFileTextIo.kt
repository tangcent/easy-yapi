package com.itangcent.easyapi.core.config

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * UTF-8 text I/O for rule files (`.properties` / `.rules` under `.easyapi/`).
 *
 * IntelliJ IDEA opens `.properties` files without a BOM as ISO-8859-1 (the
 * `java.util.Properties` default), so non-ASCII rule content — e.g. the
 * Chinese comments in AI-generated rules — renders garbled, and recent IDEA
 * builds disable the per-file encoding selector for `.properties` entirely.
 * Per the IDEA encoding rules, a UTF-8 BOM takes precedence over every other
 * setting, so files written by the plugin carry one.
 *
 * The plugin's own readers ([readUtf8StrippingBom] / [stripBom]) remove the
 * BOM before the text is parsed or displayed, so a BOM never leaks into rule
 * keys, editor content, or AI tool results.
 */
object RuleFileTextIo {

    private const val UTF8_BOM = "\uFEFF"

    /**
     * Writes [content] to [path] as UTF-8 with a leading BOM.
     *
     * A BOM already present in [content] is replaced (not duplicated), so
     * read-modify-write round trips are stable.
     */
    fun writeUtf8WithBom(path: Path, content: String) {
        val body = if (content.startsWith(UTF8_BOM)) content.substring(UTF8_BOM.length) else content
        Files.write(path, (UTF8_BOM + body).toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Reads [path] as UTF-8, stripping a leading BOM if present.
     */
    fun readUtf8StrippingBom(path: Path): String = stripBom(Files.readString(path, StandardCharsets.UTF_8))

    /**
     * Strips a single leading UTF-8 BOM character from [content].
     */
    fun stripBom(content: String): String =
        if (content.startsWith(UTF8_BOM)) content.substring(UTF8_BOM.length) else content
}
