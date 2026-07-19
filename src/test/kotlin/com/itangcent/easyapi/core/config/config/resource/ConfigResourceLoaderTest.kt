package com.itangcent.easyapi.core.config.resource

import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConfigResourceLoaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var project: Project
    private lateinit var cachedResourceResolver: CachedResourceResolver

    @Before
    fun setUp() {
        project = mock()
        cachedResourceResolver = mock()
        whenever(project.getService(CachedResourceResolver::class.java))
            .thenReturn(cachedResourceResolver)
    }

    @Test
    fun testLoadLocalAbsoluteFile() {
        val file = tempFolder.newFile("rules.properties")
        file.writeText("api.name=test")

        val loader = ConfigResourceLoader(project)
        val result = runBlocking { loader.load(file.absolutePath, null) }

        assertNotNull(result)
        assertEquals("api.name=test", result!!.content)
        assertEquals(file.parentFile.absolutePath, result.baseDir)
    }

    @Test
    fun testLoadLocalRelativeFileWithBaseDir() {
        val subDir = tempFolder.newFolder("sub")
        val file = java.io.File(subDir, "additional.properties")
        file.writeText("api.version=1.0")

        val loader = ConfigResourceLoader(project)
        val result = runBlocking { loader.load("additional.properties", subDir.absolutePath) }

        assertNotNull(result)
        assertEquals("api.version=1.0", result!!.content)
        assertEquals(subDir.absolutePath, result.baseDir)
    }

    @Test
    fun testLoadLocalFileTrimsQuotes() {
        val file = tempFolder.newFile("rules.properties")
        file.writeText("api.name=quoted")

        val loader = ConfigResourceLoader(project)
        val result = runBlocking { loader.load("\"${file.absolutePath}\"", null) }

        assertNotNull(result)
        assertEquals("api.name=quoted", result!!.content)
    }

    @Test
    fun testLoadReturnsNullForMissingFile() {
        val loader = ConfigResourceLoader(project)
        val result = runBlocking { loader.load("/nonexistent/path/to/file.properties", null) }
        assertNull(result)
    }

    @Test
    fun testLoadRemoteUrlWithResolver() {
        val url = "https://example.com/config/rules.properties"
        val content = "api.name=remote"
        runBlocking { whenever(cachedResourceResolver.get(url)).thenReturn(content) }

        val loader = ConfigResourceLoader(project)
        val result = runBlocking { loader.load(url, null) }

        assertNotNull(result)
        assertEquals(content, result!!.content)
        assertEquals("https://example.com/config", result.baseDir)
    }

    @Test
    fun testLoadRemoteUrlWhenFetchFailsReturnsNull() {
        val url = "https://example.com/config/rules.properties"
        runBlocking { whenever(cachedResourceResolver.get(url)).thenReturn(null) }

        val loader = ConfigResourceLoader(project)
        val result = runBlocking { loader.load(url, null) }
        assertNull(result)
    }

    @Test
    fun testLoadRemoteUrlBaseDirIsParentUrl() {
        val url = "https://cdn.example.com/a/b/c/rules.properties"
        runBlocking { whenever(cachedResourceResolver.get(url)).thenReturn("content") }

        val loader = ConfigResourceLoader(project)
        val result = runBlocking { loader.load(url, "/some/base") }

        assertNotNull(result)
        // baseDir should be derived from the URL, not the passed-in baseDir
        assertEquals("https://cdn.example.com/a/b/c", result!!.baseDir)
    }

    @Test
    fun testLoadRemoteUrlWithNoPathSeparator() {
        val url = "https://example.com"
        runBlocking { whenever(cachedResourceResolver.get(url)).thenReturn("content") }

        val loader = ConfigResourceLoader(project)
        val result = runBlocking { loader.load(url, null) }

        assertNotNull(result)
        assertEquals("content", result!!.content)
        // When there's no path after the host, the baseDir is the URL itself
        assertEquals(url, result.baseDir)
    }
}
