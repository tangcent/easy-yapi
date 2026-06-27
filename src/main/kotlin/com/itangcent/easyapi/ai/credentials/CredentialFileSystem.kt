package com.itangcent.easyapi.ai.credentials

import java.io.File

/**
 * Filesystem abstraction for the credential scanner.
 *
 * Single-method interface so unit tests can inject an in-memory map and
 * exercise the scanner without touching the real filesystem.
 */
fun interface CredentialFileSystem {

    /**
     * Reads the file at [path] as UTF-8 text, or returns `null` if the file
     * is absent. Never throws — callers rely on `null` to mean "miss".
     */
    fun readFile(path: String): String?
}

/**
 * Default implementation backed by `java.io.File`.
 *
 * Reads only the first 8 KB of any file to bound memory use — credential
 * files are tiny, and a multi-MB hit indicates the path pointed at the
 * wrong file (fail closed).
 */
object DefaultCredentialFileSystem : CredentialFileSystem {

    private const val MAX_BYTES = 8 * 1024

    override fun readFile(path: String): String? = runCatching {
        val file = File(path)
        if (!file.isFile) return null
        // Bound reads so a misconfigured path can't OOM the scan.
        val bytes = ByteArray(MAX_BYTES.coerceAtMost(file.length().toInt().coerceAtLeast(0)))
        file.inputStream().use { it.read(bytes) }
        String(bytes, Charsets.UTF_8).trim().trimEnd('\u0000')
    }.getOrNull()
}
