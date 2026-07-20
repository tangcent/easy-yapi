package com.itangcent.easyapi.core.settings.ui

import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.module.GrpcSettings
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
        val settings = GeneralSettings()
        // Feign (default-off) and SpringActuator (default-off) are not in
        // enabledFrameworks by default; JAX-RS (default-on) is not in
        // disabledFrameworks. Framework enablement is resolved by
        // FrameworkRegistry from these arrays overlaid on enabledByDefault.
        assertFalse(settings.enabledFrameworks.contains("Feign"))
        assertFalse(settings.disabledFrameworks.contains("JAX-RS"))
        assertFalse(settings.enabledFrameworks.contains("SpringActuator"))
        assertTrue(settings.autoScanEnabled)
        assertFalse(settings.concurrentScanEnabled)
        assertTrue(settings.gutterIconEnabled)
        assertTrue(settings.switchNotice)
        assertEquals(100, settings.logLevel)
        assertEquals("UTF-8", settings.outputCharset)
    }

    @Test
    fun testSettingsCustomValues() {
        val settings = GeneralSettings(
            enabledFrameworks = arrayOf("Feign", "SpringActuator"),
            disabledFrameworks = arrayOf("JAX-RS"),
            autoScanEnabled = false,
            concurrentScanEnabled = true,
            gutterIconEnabled = false,
            switchNotice = false,
            logLevel = 40,
            outputCharset = "GBK"
        )
        assertTrue(settings.enabledFrameworks.contains("Feign"))
        assertTrue(settings.disabledFrameworks.contains("JAX-RS"))
        assertTrue(settings.enabledFrameworks.contains("SpringActuator"))
        assertFalse(settings.autoScanEnabled)
        assertTrue(settings.concurrentScanEnabled)
        assertFalse(settings.gutterIconEnabled)
        assertFalse(settings.switchNotice)
        assertEquals(40, settings.logLevel)
        assertEquals("GBK", settings.outputCharset)
    }

    @Test
    fun testSettingsEquality() {
        val s1 = GeneralSettings()
        val s2 = GeneralSettings()
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun testSettingsInequality() {
        val s1 = GeneralSettings()
        val s2 = GeneralSettings(enabledFrameworks = arrayOf("Feign"))
        assertNotEquals(s1, s2)
    }

    @Test
    fun testSettingsCopy() {
        val s1 = GeneralSettings(enabledFrameworks = arrayOf("Feign"), logLevel = 40)
        val s2 = s1.copy(logLevel = 20)
        assertTrue(s2.enabledFrameworks.contains("Feign"))
        assertEquals(20, s2.logLevel)
        assertEquals(40, s1.logLevel) // original unchanged
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
        val settings = GeneralSettings(outputCharset = "ISO-8859-1")
        assertEquals("ISO-8859-1", settings.outputCharset)
    }

    @Test
    fun testSettingsRepositoriesArray() {
        val settings = GrpcSettings(grpcRepositories = arrayOf("maven:latest:true", "custom:/path:latest:false"))
        assertEquals(2, settings.grpcRepositories.size)
        assertEquals("maven:latest:true", settings.grpcRepositories[0])
    }

    @Test
    fun testSettingsRepositoriesEquality() {
        val s1 = GrpcSettings(grpcRepositories = arrayOf("a", "b"))
        val s2 = GrpcSettings(grpcRepositories = arrayOf("a", "b"))
        assertEquals(s1, s2)
    }

    @Test
    fun testSettingsRepositoriesInequality() {
        val s1 = GrpcSettings(grpcRepositories = arrayOf("a", "b"))
        val s2 = GrpcSettings(grpcRepositories = arrayOf("a", "c"))
        assertNotEquals(s1, s2)
    }

    // --- Task 1.2: GeneralSettings enabledChannels / disabledChannels ---

    @Test
    fun testEnabledChannelsDefaultEmpty() {
        val settings = GeneralSettings()
        assertEquals(0, settings.enabledChannels.size)
        assertEquals(0, settings.disabledChannels.size)
    }

    @Test
    fun testEnabledChannelsCustomValues() {
        val settings = GeneralSettings(
            enabledChannels = arrayOf("http-client", "hoppscotch"),
            disabledChannels = arrayOf("markdown")
        )
        assertEquals(2, settings.enabledChannels.size)
        assertEquals("http-client", settings.enabledChannels[0])
        assertEquals("hoppscotch", settings.enabledChannels[1])
        assertEquals(1, settings.disabledChannels.size)
        assertEquals("markdown", settings.disabledChannels[0])
    }

    @Test
    fun testEnabledChannelsEquality() {
        val s1 = GeneralSettings(enabledChannels = arrayOf("a", "b"), disabledChannels = arrayOf("c"))
        val s2 = GeneralSettings(enabledChannels = arrayOf("a", "b"), disabledChannels = arrayOf("c"))
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun testEnabledChannelsInequality() {
        val s1 = GeneralSettings(enabledChannels = arrayOf("a", "b"))
        val s2 = GeneralSettings(enabledChannels = arrayOf("a", "c"))
        assertNotEquals(s1, s2)

        val d1 = GeneralSettings(disabledChannels = arrayOf("x"))
        val d2 = GeneralSettings(disabledChannels = arrayOf("y"))
        assertNotEquals(d1, d2)
    }

    @Test
    fun testEnabledChannelsCopy() {
        val s1 = GeneralSettings(enabledChannels = arrayOf("http-client"), disabledChannels = arrayOf("markdown"))
        val s2 = s1.copy(disabledChannels = emptyArray())
        assertArrayEquals(arrayOf("http-client"), s2.enabledChannels)
        assertEquals(0, s2.disabledChannels.size)
        // original unchanged
        assertEquals(1, s1.disabledChannels.size)
    }

    // --- Task A.2: GeneralSettings enabledFieldFormatChannels / disabledFieldFormatChannels ---

    @Test
    fun testEnabledFieldFormatChannelsDefaultEmpty() {
        val settings = GeneralSettings()
        assertEquals(0, settings.enabledFieldFormatChannels.size)
        assertEquals(0, settings.disabledFieldFormatChannels.size)
    }

    @Test
    fun testEnabledFieldFormatChannelsCustomValues() {
        val settings = GeneralSettings(
            enabledFieldFormatChannels = arrayOf("json5", "yaml"),
            disabledFieldFormatChannels = arrayOf("json")
        )
        assertEquals(2, settings.enabledFieldFormatChannels.size)
        assertEquals("json5", settings.enabledFieldFormatChannels[0])
        assertEquals("yaml", settings.enabledFieldFormatChannels[1])
        assertEquals(1, settings.disabledFieldFormatChannels.size)
        assertEquals("json", settings.disabledFieldFormatChannels[0])
    }

    @Test
    fun testEnabledFieldFormatChannelsEquality() {
        val s1 = GeneralSettings(
            enabledFieldFormatChannels = arrayOf("a", "b"),
            disabledFieldFormatChannels = arrayOf("c")
        )
        val s2 = GeneralSettings(
            enabledFieldFormatChannels = arrayOf("a", "b"),
            disabledFieldFormatChannels = arrayOf("c")
        )
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun testEnabledFieldFormatChannelsInequality() {
        val s1 = GeneralSettings(enabledFieldFormatChannels = arrayOf("a", "b"))
        val s2 = GeneralSettings(enabledFieldFormatChannels = arrayOf("a", "c"))
        assertNotEquals(s1, s2)

        val d1 = GeneralSettings(disabledFieldFormatChannels = arrayOf("x"))
        val d2 = GeneralSettings(disabledFieldFormatChannels = arrayOf("y"))
        assertNotEquals(d1, d2)
    }

    @Test
    fun testEnabledFieldFormatChannelsCopy() {
        val s1 = GeneralSettings(
            enabledFieldFormatChannels = arrayOf("json5"),
            disabledFieldFormatChannels = arrayOf("json")
        )
        val s2 = s1.copy(disabledFieldFormatChannels = emptyArray())
        assertArrayEquals(arrayOf("json5"), s2.enabledFieldFormatChannels)
        assertEquals(0, s2.disabledFieldFormatChannels.size)
        // original unchanged
        assertEquals(1, s1.disabledFieldFormatChannels.size)
    }

    @Test
    fun testFieldFormatChannelsIndependentFromExportChannels() {
        // The two EPs use independent id spaces (Decision A4). Setting one pair
        // must not affect the other.
        val settings = GeneralSettings(
            enabledChannels = arrayOf("markdown"),
            disabledChannels = arrayOf("postman"),
            enabledFieldFormatChannels = arrayOf("json"),
            disabledFieldFormatChannels = arrayOf("yaml")
        )
        assertEquals(listOf("markdown"), settings.enabledChannels.toList())
        assertEquals(listOf("postman"), settings.disabledChannels.toList())
        assertEquals(listOf("json"), settings.enabledFieldFormatChannels.toList())
        assertEquals(listOf("yaml"), settings.disabledFieldFormatChannels.toList())
    }
}
