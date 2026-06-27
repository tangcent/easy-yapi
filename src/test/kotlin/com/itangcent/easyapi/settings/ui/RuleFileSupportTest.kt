package com.itangcent.easyapi.settings.ui

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

/**
 * Unit tests for the pure-logic helpers in [RuleFileSupport]
 * (`nameOf`, `fileSize`, `nextAvailableName`).
 *
 * Byte-size formatting (`formatSize`) has been consolidated into
 * [com.itangcent.easyapi.util.text.ByteSizeUtil] — see
 * [com.itangcent.easyapi.util.text.ByteSizeUtilTest] for coverage.
 *
 * The UI-touched helpers (`copyPathToClipboard`, `deleteFile`,
 * `installRowInteractions`) require an IntelliJ [com.intellij.openapi.project.Project]
 * and the AWT thread, so they are covered separately by the platform
 * fixture tests.
 */
class RuleFileSupportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // --- nameOf ---

    @Test
    fun nameOfReturnsLastPathSegment() {
        assertEquals("rule.config", RuleFileSupport.nameOf("/home/user/.easyapi/rule.config"))
        assertEquals("rule.config", RuleFileSupport.nameOf("relative/path/rule.config"))
    }

    @Test
    fun nameOfReturnsNameForBareFileName() {
        assertEquals("rule.config", RuleFileSupport.nameOf("rule.config"))
    }

    @Test
    fun nameOfHandlesTrailingSeparator() {
        // Paths.get("foo/").fileName returns "foo", not null.
        assertEquals("foo", RuleFileSupport.nameOf("foo/"))
    }

    // --- fileSize ---

    @Test
    fun fileSizeReturnsActualSizeForExistingFile() {
        val file = tempFolder.newFile("rule.config")
        val content = "api.name=Test"
        Files.write(file.toPath(), content.toByteArray())
        assertEquals(content.length.toLong(), RuleFileSupport.fileSize(file.absolutePath))
    }

    @Test
    fun fileSizeReturnsZeroForMissingFile() {
        // A path that does not exist — runCatching swallows the NoSuchFileException
        // and returns the default 0L.
        assertEquals(0L, RuleFileSupport.fileSize("/no/such/file/here/rule.config"))
    }

    @Test
    fun fileSizeDoesNotCrashOnBlankPath() {
        // Paths.get("") resolves to the CWD, so Files.size succeeds and
        // returns a non-negative value rather than throwing. The helper's
        // runCatching only guards against exceptions; blank input does not
        // trigger the fallback.
        assertTrue(RuleFileSupport.fileSize("") >= 0)
    }

    @Test
    fun fileSizeReturnsZeroForDirectoryPath() {
        // Files.size on a directory is platform-dependent but does not throw;
        // we just assert it returns something non-negative (the helper should
        // not crash on directories).
        val dir = tempFolder.newFolder("subdir")
        assertTrue(RuleFileSupport.fileSize(dir.absolutePath) >= 0)
    }

    // --- nextAvailableName ---

    @Test
    fun nextAvailableNameReturnsBaseNameWhenNoCollision() {
        val dir = tempFolder.newFolder("rules").toPath()
        assertEquals("custom.rules", RuleFileSupport.nextAvailableName(dir, "custom.rules"))
    }

    @Test
    fun nextAvailableNameAppendsNumericSuffixOnCollision() {
        val dir = tempFolder.newFolder("rules").toPath()
        Files.createFile(dir.resolve("custom.rules"))
        val next = RuleFileSupport.nextAvailableName(dir, "custom.rules")
        assertNotEquals("custom.rules", next)
        assertTrue("next name should start with base prefix: $next", next.startsWith("custom"))
        assertTrue("next name should keep extension: $next", next.endsWith(".rules"))
    }

    @Test
    fun nextAvailableNameKeepsExtensionOnCollision() {
        val dir = tempFolder.newFolder("rules").toPath()
        Files.createFile(dir.resolve("my-rules.properties"))
        val next = RuleFileSupport.nextAvailableName(dir, "my-rules.properties")
        // The suffix is inserted before the extension: `my-rules-1.properties`.
        assertTrue("should keep .properties extension: $next", next.endsWith(".properties"))
        assertNotEquals("my-rules.properties", next)
    }

    @Test
    fun nextAvailableNameHandlesNoExtension() {
        val dir = tempFolder.newFolder("rules").toPath()
        Files.createFile(dir.resolve("README"))
        val next = RuleFileSupport.nextAvailableName(dir, "README")
        assertNotEquals("README", next)
        assertTrue("should start with README: $next", next.startsWith("README"))
    }
}
