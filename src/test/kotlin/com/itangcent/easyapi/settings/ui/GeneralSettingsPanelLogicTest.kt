package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.MarkdownFormatType
import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Test

class GeneralSettingsPanelLogicTest {

    // --- CommonSettingsHelper.VerbosityLevel tests ---

    @Test
    fun testVerbosityLevel_toLevel_exactMatch() {
        assertEquals(
            CommonSettingsHelper.VerbosityLevel.SILENT,
            CommonSettingsHelper.VerbosityLevel.toLevel(100)
        )
        assertEquals(
            CommonSettingsHelper.VerbosityLevel.ERROR,
            CommonSettingsHelper.VerbosityLevel.toLevel(40)
        )
        assertEquals(
            CommonSettingsHelper.VerbosityLevel.WARN,
            CommonSettingsHelper.VerbosityLevel.toLevel(30)
        )
        assertEquals(
            CommonSettingsHelper.VerbosityLevel.INFO,
            CommonSettingsHelper.VerbosityLevel.toLevel(20)
        )
        assertEquals(
            CommonSettingsHelper.VerbosityLevel.DEBUG,
            CommonSettingsHelper.VerbosityLevel.toLevel(10)
        )
        assertEquals(
            CommonSettingsHelper.VerbosityLevel.TRACE,
            CommonSettingsHelper.VerbosityLevel.toLevel(0)
        )
    }

    @Test
    fun testVerbosityLevel_toLevel_closestMatch() {
        // 25 is closest to INFO(20) or WARN(30) — both distance 5; minByOrNull picks first
        val result = CommonSettingsHelper.VerbosityLevel.toLevel(25)
        assertNotNull(result)
        // Distance to WARN(30) = 5, INFO(20) = 5 — minByOrNull picks whichever comes first
        assertTrue(result == CommonSettingsHelper.VerbosityLevel.WARN || result == CommonSettingsHelper.VerbosityLevel.INFO)
    }

    @Test
    fun testVerbosityLevel_toLevel_unknownHighValue() {
        // 200 is closest to SILENT(100)
        assertEquals(
            CommonSettingsHelper.VerbosityLevel.SILENT,
            CommonSettingsHelper.VerbosityLevel.toLevel(200)
        )
    }

    @Test
    fun testVerbosityLevel_toLevel_negativeValue() {
        // -10 is closest to TRACE(0)
        assertEquals(
            CommonSettingsHelper.VerbosityLevel.TRACE,
            CommonSettingsHelper.VerbosityLevel.toLevel(-10)
        )
    }

    @Test
    fun testVerbosityLevel_displayName() {
        assertEquals("Silent", CommonSettingsHelper.VerbosityLevel.SILENT.displayName)
        assertEquals("Error", CommonSettingsHelper.VerbosityLevel.ERROR.displayName)
        assertEquals("Warning", CommonSettingsHelper.VerbosityLevel.WARN.displayName)
        assertEquals("Info", CommonSettingsHelper.VerbosityLevel.INFO.displayName)
        assertEquals("Debug", CommonSettingsHelper.VerbosityLevel.DEBUG.displayName)
        assertEquals("Trace", CommonSettingsHelper.VerbosityLevel.TRACE.displayName)
    }

    @Test
    fun testVerbosityLevel_toString_returnsDisplayName() {
        assertEquals("Silent", CommonSettingsHelper.VerbosityLevel.SILENT.toString())
        assertEquals("Trace", CommonSettingsHelper.VerbosityLevel.TRACE.toString())
    }

    // --- GeneralSettingsPanel resetFrom/applyTo/isModified logic ---
    // Note: GeneralSettingsPanel requires a Project for cache operations.
    // We test the logic through a lightweight approach by verifying
    // the Settings data flow without creating the full panel.

    @Test
    fun testSettingsDefaultValues() {
        val settings = Settings()
        assertFalse(settings.feignEnable)
        assertTrue(settings.jaxrsEnable)
        assertFalse(settings.actuatorEnable)
        assertTrue(settings.autoScanEnabled)
        assertFalse(settings.concurrentScanEnabled)
        assertTrue(settings.gutterIconEnabled)
        assertTrue(settings.switchNotice)
        assertEquals(100, settings.logLevel)
        assertEquals("UTF-8", settings.outputCharset)
        assertTrue(settings.outputDemo)
        assertEquals(MarkdownFormatType.SIMPLE.name, settings.markdownFormatType)
    }

    @Test
    fun testSettingsCustomValues() {
        val settings = Settings(
            feignEnable = true,
            jaxrsEnable = false,
            actuatorEnable = true,
            autoScanEnabled = false,
            concurrentScanEnabled = true,
            gutterIconEnabled = false,
            switchNotice = false,
            logLevel = 40,
            outputCharset = "GBK",
            outputDemo = false,
            markdownFormatType = MarkdownFormatType.ULTIMATE.name
        )
        assertTrue(settings.feignEnable)
        assertFalse(settings.jaxrsEnable)
        assertTrue(settings.actuatorEnable)
        assertFalse(settings.autoScanEnabled)
        assertTrue(settings.concurrentScanEnabled)
        assertFalse(settings.gutterIconEnabled)
        assertFalse(settings.switchNotice)
        assertEquals(40, settings.logLevel)
        assertEquals("GBK", settings.outputCharset)
        assertFalse(settings.outputDemo)
        assertEquals(MarkdownFormatType.ULTIMATE.name, settings.markdownFormatType)
    }

    @Test
    fun testSettingsEquality() {
        val s1 = Settings()
        val s2 = Settings()
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun testSettingsInequality() {
        val s1 = Settings()
        val s2 = Settings(feignEnable = true)
        assertNotEquals(s1, s2)
    }

    @Test
    fun testSettingsCopy() {
        val s1 = Settings(feignEnable = true, logLevel = 40)
        val s2 = s1.copy(logLevel = 20)
        assertTrue(s2.feignEnable)
        assertEquals(20, s2.logLevel)
        assertEquals(40, s1.logLevel) // original unchanged
    }

    @Test
    fun testMarkdownFormatType_values() {
        val values = MarkdownFormatType.values()
        assertEquals(2, values.size)
        assertEquals(MarkdownFormatType.SIMPLE, values[0])
        assertEquals(MarkdownFormatType.ULTIMATE, values[1])
    }

    @Test
    fun testMarkdownFormatType_valueOf() {
        assertEquals(MarkdownFormatType.SIMPLE, MarkdownFormatType.valueOf("SIMPLE"))
        assertEquals(MarkdownFormatType.ULTIMATE, MarkdownFormatType.valueOf("ULTIMATE"))
    }

    @Test
    fun testMarkdownFormatType_desc() {
        assertNotNull(MarkdownFormatType.SIMPLE.desc)
        assertNotNull(MarkdownFormatType.ULTIMATE.desc)
    }

    @Test
    fun testSettingsLogLevelMapping() {
        // Test that logLevel values map correctly through VerbosityLevel
        for (level in CommonSettingsHelper.VerbosityLevel.values()) {
            val resolved = CommonSettingsHelper.VerbosityLevel.toLevel(level.level)
            assertEquals(level, resolved)
        }
    }

    @Test
    fun testSettingsOutputCharsetValues() {
        val settings = Settings(outputCharset = "ISO-8859-1")
        assertEquals("ISO-8859-1", settings.outputCharset)
    }

    @Test
    fun testSettingsRepositoriesArray() {
        val settings = Settings(grpcRepositories = arrayOf("maven:latest:true", "custom:/path:latest:false"))
        assertEquals(2, settings.grpcRepositories.size)
        assertEquals("maven:latest:true", settings.grpcRepositories[0])
    }

    @Test
    fun testSettingsRepositoriesEquality() {
        val s1 = Settings(grpcRepositories = arrayOf("a", "b"))
        val s2 = Settings(grpcRepositories = arrayOf("a", "b"))
        assertEquals(s1, s2)
    }

    @Test
    fun testSettingsRepositoriesInequality() {
        val s1 = Settings(grpcRepositories = arrayOf("a", "b"))
        val s2 = Settings(grpcRepositories = arrayOf("a", "c"))
        assertNotEquals(s1, s2)
    }
}
