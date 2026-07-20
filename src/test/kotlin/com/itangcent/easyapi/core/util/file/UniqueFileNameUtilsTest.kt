package com.itangcent.easyapi.core.util.file

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class UniqueFileNameUtilsTest {

    private fun tempDir(): Path =
        Files.createTempDirectory("unique-name-").also { it.toFile().deleteOnExit() }

    private fun touch(dir: Path, name: String) {
        Files.createFile(dir.resolve(name))
    }

    @Test
    fun returnsNameWhenNotExists() {
        val dir = tempDir()
        assertEquals("xx.yy", UniqueFileNameUtils.uniqueFileName(dir, "xx.yy"))
    }

    @Test
    fun appendsSuffixWhenNameExists() {
        val dir = tempDir()
        touch(dir, "xx.yy")
        assertEquals("xx-1.yy", UniqueFileNameUtils.uniqueFileName(dir, "xx.yy"))
    }

    @Test
    fun incrementsSuffixUntilUnique() {
        val dir = tempDir()
        touch(dir, "xx.yy")
        touch(dir, "xx-1.yy")
        touch(dir, "xx-2.yy")
        assertEquals("xx-3.yy", UniqueFileNameUtils.uniqueFileName(dir, "xx.yy"))
    }

    @Test
    fun fillsGapAfterDeletion() {
        val dir = tempDir()
        touch(dir, "xx.yy")
        touch(dir, "xx-1.yy")
        // xx-1 removed → the first free slot is xx-1 again.
        Files.delete(dir.resolve("xx-1.yy"))
        assertEquals("xx-1.yy", UniqueFileNameUtils.uniqueFileName(dir, "xx.yy"))
    }

    @Test
    fun handlesNoExtension() {
        val dir = tempDir()
        touch(dir, "Makefile")
        assertEquals("Makefile-1", UniqueFileNameUtils.uniqueFileName(dir, "Makefile"))
        touch(dir, "Makefile-1")
        assertEquals("Makefile-2", UniqueFileNameUtils.uniqueFileName(dir, "Makefile"))
    }

    @Test
    fun handlesHiddenFileWithSingleLeadingDot() {
        val dir = tempDir()
        touch(dir, ".gitignore")
        assertEquals(".gitignore-1", UniqueFileNameUtils.uniqueFileName(dir, ".gitignore"))
    }

    @Test
    fun preservesMultipleDotsInBase() {
        val dir = tempDir()
        touch(dir, ".easy.api.properties")
        assertEquals(
            ".easy.api-1.properties",
            UniqueFileNameUtils.uniqueFileName(dir, ".easy.api.properties")
        )
    }

    @Test
    fun chainsSuffixesAcrossCollisions() {
        val dir = tempDir()
        touch(dir, ".easy.api.properties")
        touch(dir, ".easy.api-1.properties")
        touch(dir, ".easy.api-2.properties")
        assertEquals(
            ".easy.api-3.properties",
            UniqueFileNameUtils.uniqueFileName(dir, ".easy.api.properties")
        )
    }

    @Test
    fun doesNotCreateFile() {
        val dir = tempDir()
        UniqueFileNameUtils.uniqueFileName(dir, "never.yy")
        assertFalse("no file should be created on disk", Files.exists(dir.resolve("never.yy")))
    }
}
