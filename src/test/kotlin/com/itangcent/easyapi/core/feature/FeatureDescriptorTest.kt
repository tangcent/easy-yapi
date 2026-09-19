package com.itangcent.easyapi.core.feature

import com.itangcent.easyapi.core.settings.module.GeneralSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureDescriptorTest {

    @Test
    fun testDirectBooleanBridgeReadsAndWritesOnlySupportedFields() {
        DirectBooleanSetting.entries.forEach { setting ->
            val settings = GeneralSettings(
                apiScanEnabled = false,
                autoScanEnabled = false,
                concurrentScanEnabled = false,
                gutterIconEnabled = false,
                copyApiUrlEnabled = false,
                searchEverywhereEnabled = false,
                switchNotice = true,
                enabledChannels = arrayOf("unchanged")
            )
            val bridge = DirectBooleanStateBridge(setting)
            val descriptor = testFeatureDescriptor(
                id = "direct-${setting.name.lowercase()}",
                defaultEnabled = false,
                bridge = bridge
            )
            val batch = FeatureStateWriteBatch(setOf(descriptor.id))

            assertEquals("Direct bridge should read the current field", false, bridge.readDesired(settings, true))
            bridge.stageWrite(true, descriptor, batch)
            batch.applyTo(settings)

            assertEquals(
                "API scanning field should match only its bridge",
                setting == DirectBooleanSetting.API_SCAN_ENABLED,
                settings.apiScanEnabled
            )
            assertEquals(
                "Automatic scanning field should match only its bridge",
                setting == DirectBooleanSetting.AUTO_SCAN_ENABLED,
                settings.autoScanEnabled
            )
            assertEquals(
                "Concurrent scanning field should match only its bridge",
                setting == DirectBooleanSetting.CONCURRENT_SCAN_ENABLED,
                settings.concurrentScanEnabled
            )
            assertEquals(
                "Gutter field should match only its bridge",
                setting == DirectBooleanSetting.GUTTER_ICON_ENABLED,
                settings.gutterIconEnabled
            )
            assertEquals(
                "Copy API URL field should match only its bridge",
                setting == DirectBooleanSetting.COPY_API_URL_ENABLED,
                settings.copyApiUrlEnabled
            )
            assertEquals(
                "Search Everywhere field should match only its bridge",
                setting == DirectBooleanSetting.SEARCH_EVERYWHERE_ENABLED,
                settings.searchEverywhereEnabled
            )
            assertEquals("Unrelated Boolean fields must remain unchanged", true, settings.switchNotice)
            assertEquals("Unrelated arrays must remain unchanged", listOf("unchanged"), settings.enabledChannels.toList())
        }
    }
}
