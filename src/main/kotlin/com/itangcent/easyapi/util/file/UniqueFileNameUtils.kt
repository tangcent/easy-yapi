package com.itangcent.easyapi.util.file

import java.nio.file.Files
import java.nio.file.Path

/**
 * Utility for generating unique, non-colliding file names within a directory.
 */
object UniqueFileNameUtils {

    /**
     * Computes a non-colliding file name in [dir] for the desired [name].
     *
     * If `dir/name` does not exist, returns [name] unchanged. Otherwise inserts
     * a numeric suffix before the file extension, incrementing until a free
     * name is found:
     *
     * `xx.yy` → `xx-1.yy` → `xx-2.yy` → …
     *
     * Names with no extension (or hidden files whose only dot is the leading
     * character, e.g. `.gitignore`) receive the suffix at the end:
     * `Makefile` → `Makefile-1`, `.gitignore` → `.gitignore-1`.
     *
     * @param dir The directory in which the file name must be unique.
     * @param name The desired file name (a single path segment, not a full path).
     * @return A file name that does not yet exist in [dir].
     */
    fun uniqueFileName(dir: Path, name: String): String {
        if (!Files.exists(dir.resolve(name))) return name
        val dotIndex = name.lastIndexOf('.')
        val base: String
        val ext: String
        if (dotIndex <= 0) {
            // No dot, or a leading-dot hidden name with no further extension.
            base = name
            ext = ""
        } else {
            base = name.substring(0, dotIndex)
            ext = name.substring(dotIndex + 1)
        }
        var index = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$base-$index" else "$base-$index.$ext"
            if (!Files.exists(dir.resolve(candidate))) return candidate
            index++
        }
    }
}
