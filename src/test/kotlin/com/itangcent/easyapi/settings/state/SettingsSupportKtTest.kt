package com.itangcent.easyapi.settings.state

import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Test

class SettingsSupportKtTest {

    @Test
    fun testPostmanCollectionsAsPairs_null() {
        val settings = Settings(postmanCollections = null)
        val pairs = settings.postmanCollectionsAsPairs()
        assertTrue(pairs.isEmpty())
    }

    @Test
    fun testPostmanCollectionsAsPairs_empty() {
        val settings = Settings(postmanCollections = "")
        val pairs = settings.postmanCollectionsAsPairs()
        assertTrue(pairs.isEmpty())
    }

    @Test
    fun testSetPostmanCollectionsPairs_andRead() {
        val settings = Settings()
        settings.setPostmanCollectionsPairs(listOf("MyModule" to "col-123"))
        val pairs = settings.postmanCollectionsAsPairs()
        assertEquals(1, pairs.size)
        assertEquals("MyModule", pairs[0].first)
        assertEquals("col-123", pairs[0].second)
    }

    @Test
    fun testSetPostmanCollectionsPairs_multiple() {
        val settings = Settings()
        settings.setPostmanCollectionsPairs(listOf(
            "Module1" to "id-1",
            "Module2" to "id-2"
        ))
        val pairs = settings.postmanCollectionsAsPairs()
        assertEquals(2, pairs.size)
        val map = pairs.toMap()
        assertEquals("id-1", map["Module1"])
        assertEquals("id-2", map["Module2"])
    }

    @Test
    fun testAddPostmanCollections() {
        val settings = Settings()
        settings.addPostmanCollections("Module1", "id-1")
        settings.addPostmanCollections("Module2", "id-2")
        val pairs = settings.postmanCollectionsAsPairs()
        val map = pairs.toMap()
        assertEquals("id-1", map["Module1"])
        assertEquals("id-2", map["Module2"])
    }

    @Test
    fun testDefaultPostmanExportMode() {
        val mode = defaultPostmanExportMode()
        assertEquals("CREATE_NEW", mode)
    }

    @Test
    fun testProjectSettingsSupport_copyTo() {
        val source = Settings(
            postmanWorkspace = "workspace-1",
            postmanExportMode = "UPDATE_EXISTING",
            postmanCollections = "Module=id",
            postmanBuildExample = false
        )
        val target = Settings()
        source.copyTo(target as ProjectSettingsSupport)
        assertEquals("workspace-1", target.postmanWorkspace)
        assertEquals("UPDATE_EXISTING", target.postmanExportMode)
        assertEquals("Module=id", target.postmanCollections)
        assertFalse(target.postmanBuildExample)
    }

    @Test
    fun testApplicationSettingsSupport_copyTo() {
        val source = Settings(
            feignEnable = true,
            jaxrsEnable = false,
            httpTimeOut = 30,
            logLevel = 100,
            outputCharset = "ISO-8859-1"
        )
        val target = Settings()
        source.copyTo(target as ApplicationSettingsSupport)
        assertTrue(target.feignEnable)
        assertFalse(target.jaxrsEnable)
        assertEquals(30, target.httpTimeOut)
        assertEquals(100, target.logLevel)
        assertEquals("ISO-8859-1", target.outputCharset)
    }
}
