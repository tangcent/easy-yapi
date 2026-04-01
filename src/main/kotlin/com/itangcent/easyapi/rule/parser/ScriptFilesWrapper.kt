package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.rule.context.FilesContext
import java.io.File
import java.nio.charset.Charset

/**
 * Wraps [FilesContext] to expose the same API as the legacy
 * `StandardJdkRuleParser.Files` class for script compatibility.
 *
 * Legacy methods:
 * - `files.save(content, path)`
 * - `files.save(content, charset, path)`
 */
object ScriptFilesWrapper {

    fun save(content: String, path: String) {
        save(content, Charsets.UTF_8, path)
    }

    fun save(content: String, charset: String, path: String) {
        val cs = runCatching { Charset.forName(charset) }.getOrDefault(Charsets.UTF_8)
        save(content, cs, path)
    }

    private fun save(content: String, charset: Charset, path: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(content.toByteArray(charset))
    }
}
