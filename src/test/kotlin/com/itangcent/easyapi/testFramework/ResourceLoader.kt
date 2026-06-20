package com.itangcent.easyapi.testFramework

import java.io.InputStream

/**
 * Low-level loader for test resources (golden files, expected output, etc.).
 *
 * This is the single place that turns a classpath resource into a normalized
 * `String`. It guarantees platform-stable comparisons by:
 * 1. Reading the stream as UTF-8.
 * 2. Collapsing CRLF (`\r\n`) to LF (`\n`) — `.txt` resources may be checked
 *    out with Windows line endings (`git config core.autocrlf=true`), while the
 *    code under test emits literal `\n`. Without this, golden-file assertions
 *    fail on Windows runners even though the output is correct.
 * 3. Trimming trailing whitespace (so files may end in a newline).
 *
 * Higher-level helpers (e.g. [ResultLoader], which derives the resource path
 * from the caller's class name) delegate the actual read to this object so the
 * normalization logic is defined exactly once.
 */
object ResourceLoader {

    /**
     * Reads a classpath resource as a normalized string.
     *
     * @param resourcePath the absolute classpath path (e.g. `/result/foo.txt`).
     * @param loader the [ClassLoader] used to find the resource; defaults to
     *   this class's loader. Pass an explicit class loader when the resource
     *   lives in another module's classpath.
     * @return the resource content with CRLF collapsed to LF and trailing
     *   whitespace removed.
     * @throws AssertionError if the resource cannot be found.
     */
    fun read(resourcePath: String, loader: ClassLoader = javaClass.classLoader): String {
        val stream: InputStream = loader.getResourceAsStream(resourcePath.removePrefix("/"))
            ?: throw AssertionError("Resource not found: $resourcePath")
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
                .replace("\r\n", "\n")
                .trimEnd()
        }
    }
}
