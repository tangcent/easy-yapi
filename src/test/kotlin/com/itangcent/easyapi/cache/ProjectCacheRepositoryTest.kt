package com.itangcent.easyapi.cache

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*
import java.nio.file.Files

class ProjectCacheRepositoryTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var repository: ProjectCacheRepository

    override fun setUp() {
        super.setUp()
        repository = ProjectCacheRepository.getInstance(project)
        repository.clear()
    }

    override fun tearDown() {
        repository.clear()
        super.tearDown()
    }

    fun testGetInstance() {
        assertNotNull(repository)
        assertSame(repository, ProjectCacheRepository.getInstance(project))
    }

    fun testReadNonExistentKey() {
        assertNull(repository.read("nonexistent.txt"))
    }

    fun testWriteAndRead() {
        repository.write("test.txt", "Hello, World!")
        assertEquals("Hello, World!", repository.read("test.txt"))
    }

    fun testOverwriteValue() {
        repository.write("test.txt", "value1")
        repository.write("test.txt", "value2")
        assertEquals("value2", repository.read("test.txt"))
    }

    fun testDelete() {
        repository.write("test.txt", "value")
        repository.delete("test.txt")
        assertNull(repository.read("test.txt"))
    }

    fun testDeleteNonExistent() {
        repository.delete("nonexistent.txt")
    }

    fun testClear() {
        repository.write("file1.txt", "value1")
        repository.write("file2.txt", "value2")
        repository.clear()
        assertNull(repository.read("file1.txt"))
        assertNull(repository.read("file2.txt"))
    }

    fun testCacheSize() {
        assertEquals(0L, repository.cacheSize())
        
        repository.write("test.txt", "Hello, World!")
        assertTrue(repository.cacheSize() > 0)
    }

    fun testCacheSizeAfterClear() {
        repository.write("test.txt", "Hello, World!")
        repository.clear()
        assertEquals(0L, repository.cacheSize())
    }

    fun testResolve() {
        val path = repository.resolve("subdir/file.txt")
        assertTrue(path.endsWith(java.nio.file.Paths.get("subdir", "file.txt")))
        assertTrue(Files.exists(path.parent))
    }

    fun testNestedPath() {
        repository.write("deep/nested/path/file.txt", "nested content")
        assertEquals("nested content", repository.read("deep/nested/path/file.txt"))
    }

    fun testEmptyValue() {
        repository.write("empty.txt", "")
        assertEquals("", repository.read("empty.txt"))
    }

    fun testSpecialCharactersInKey() {
        repository.write("key-with.special_chars.txt", "value")
        assertEquals("value", repository.read("key-with.special_chars.txt"))
    }

    fun testLargeContent() {
        val largeContent = "a".repeat(100000)
        repository.write("large.txt", largeContent)
        assertEquals(largeContent, repository.read("large.txt"))
    }

    fun testUnicodeContent() {
        val unicodeContent = "你好世界 🌍 Hello"
        repository.write("unicode.txt", unicodeContent)
        assertEquals(unicodeContent, repository.read("unicode.txt"))
    }

    fun testMultipleFiles() {
        repository.write("file1.txt", "value1")
        repository.write("file2.txt", "value2")
        repository.write("file3.txt", "value3")

        assertEquals("value1", repository.read("file1.txt"))
        assertEquals("value2", repository.read("file2.txt"))
        assertEquals("value3", repository.read("file3.txt"))
    }
}
