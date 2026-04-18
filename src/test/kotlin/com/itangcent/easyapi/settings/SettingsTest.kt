package com.itangcent.easyapi.settings

import com.itangcent.easyapi.settings.state.ApplicationSettingsSupport
import com.itangcent.easyapi.settings.state.ProjectSettingsSupport
import org.junit.Assert.*
import org.junit.Test

class SettingsTest {

    @Test
    fun testDefaultSettings() {
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
    fun testSettingsCopyToApplicationSettings() {
        val source = Settings(feignEnable = true, httpTimeOut = 10)
        val target = Settings()
        (source as ApplicationSettingsSupport).copyTo(target)
        assertTrue("feignEnable should be copied", target.feignEnable)
        assertEquals("httpTimeOut should be copied", 10, target.httpTimeOut)
    }

    @Test
    fun testSettingsCopyToProjectSettings() {
        val source = Settings(postmanWorkspace = "workspace-123", postmanExportMode = "UPDATE")
        val target = Settings()
        (source as ProjectSettingsSupport).copyTo(target)
        assertEquals("postmanWorkspace should be copied", "workspace-123", target.postmanWorkspace)
        assertEquals("postmanExportMode should be copied", "UPDATE", target.postmanExportMode)
    }

    @Test
    fun testSettingsModification() {
        val settings = Settings()
        settings.feignEnable = true
        settings.httpTimeOut = 30
        settings.postmanToken = "test-token"
        assertTrue("feignEnable should be modified", settings.feignEnable)
        assertEquals("httpTimeOut should be modified", 30, settings.httpTimeOut)
        assertEquals("postmanToken should be modified", "test-token", settings.postmanToken)
    }
}
