package com.itangcent.easyapi.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsTest {

    @Test
    fun testDefaultValues() {
        val settings = Settings()
        assertFalse(settings.feignEnable)
        assertTrue(settings.jaxrsEnable)
        assertFalse(settings.actuatorEnable)
        assertTrue(settings.grpcEnable)
        assertTrue(settings.swaggerEnable)
        assertTrue(settings.swagger3Enable)
        assertNull(settings.postmanToken)
        assertNull(settings.postmanWorkspace)
        assertEquals(PostmanExportMode.CREATE_NEW.name, settings.postmanExportMode)
        assertNull(settings.postmanCollections)
        assertTrue(settings.postmanBuildExample)
        assertFalse(settings.wrapCollection)
        assertFalse(settings.autoMergeScript)
        assertTrue(settings.queryExpanded)
        assertTrue(settings.formExpanded)
        assertEquals("ALL", settings.pathMulti)
        assertTrue(settings.inferReturnMain)
        assertTrue(settings.enableUrlTemplating)
        assertTrue(settings.switchNotice)
        assertEquals(30, settings.httpTimeOut)
        assertFalse(settings.unsafeSsl)
        assertEquals(HttpClientType.APACHE.value, settings.httpClient)
        assertEquals(50, settings.logLevel)
        assertTrue(settings.outputDemo)
        assertEquals("UTF-8", settings.outputCharset)
        assertEquals(MarkdownFormatType.SIMPLE.name, settings.markdownFormatType)
        assertNull(settings.builtInConfig)
        assertArrayEquals(emptyArray(), settings.remoteConfig)
        assertTrue(settings.autoScanEnabled)
        assertArrayEquals(emptyArray(), settings.grpcArtifactConfigs)
        assertArrayEquals(emptyArray(), settings.grpcAdditionalJars)
        assertFalse(settings.grpcCallEnabled)
        assertArrayEquals(emptyArray(), settings.grpcRepositories)
    }

    @Test
    fun testCustomValues() {
        val settings = Settings(
            feignEnable = true,
            jaxrsEnable = false,
            swaggerEnable = false,
            swagger3Enable = false,
            postmanToken = "my-token",
            httpTimeOut = 30,
            logLevel = 100
        )
        assertTrue(settings.feignEnable)
        assertFalse(settings.jaxrsEnable)
        assertFalse(settings.swaggerEnable)
        assertFalse(settings.swagger3Enable)
        assertEquals("my-token", settings.postmanToken)
        assertEquals(30, settings.httpTimeOut)
        assertEquals(100, settings.logLevel)
    }

    @Test
    fun testEquality_sameDefaults() {
        val s1 = Settings()
        val s2 = Settings()
        assertEquals(s1, s2)
    }

    @Test
    fun testEquality_differentValues() {
        val s1 = Settings(feignEnable = true)
        val s2 = Settings(feignEnable = false)
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEquality_differentSwaggerEnable() {
        val s1 = Settings(swaggerEnable = true)
        val s2 = Settings(swaggerEnable = false)
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEquality_differentSwagger3Enable() {
        val s1 = Settings(swagger3Enable = true)
        val s2 = Settings(swagger3Enable = false)
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEquality_withArrays() {
        val s1 = Settings(remoteConfig = arrayOf("http://example.com"))
        val s2 = Settings(remoteConfig = arrayOf("http://example.com"))
        assertEquals(s1, s2)
    }

    @Test
    fun testEquality_differentArrays() {
        val s1 = Settings(remoteConfig = arrayOf("http://a.com"))
        val s2 = Settings(remoteConfig = arrayOf("http://b.com"))
        assertNotEquals(s1, s2)
    }

    @Test
    fun testHashCode_sameDefaults() {
        val s1 = Settings()
        val s2 = Settings()
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun testHashCode_differentValues() {
        val s1 = Settings(feignEnable = true)
        val s2 = Settings(feignEnable = false)
        assertNotEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun testEquality_sameInstance() {
        val s = Settings()
        assertEquals(s, s)
    }

    @Test
    fun testEquality_null() {
        val s = Settings()
        assertNotEquals(s, null)
    }

    @Test
    fun testEquality_differentType() {
        val s = Settings()
        assertNotEquals(s, "not a settings")
    }

    @Test
    fun testExtensionConfigs_notEmpty() {
        val settings = Settings()
        assertNotNull(settings.extensionConfigs)
    }
}
