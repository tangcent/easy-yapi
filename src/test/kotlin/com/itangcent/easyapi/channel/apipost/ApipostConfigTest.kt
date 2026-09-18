package com.itangcent.easyapi.channel.apipost

import com.itangcent.easyapi.channel.spi.ChannelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.memberProperties

/**
 * Contract tests for [ApipostConfig] (plain JUnit — no IntelliJ fixture needed).
 *
 * Pins the decision that [ApipostConfig] carries **only** `selectedToken` and
 * `projectId`:
 *
 * - no `outputDir` / `fileName` — they live on the final
 *   [ChannelConfig.FileConfig], which this type cannot extend, so duplicating
 *   them makes `handleResult`'s cast silently null (P0-2);
 * - no `exportMode` — every API is upserted individually, so there is nothing
 *   to choose (P2-7).
 *
 * The panel that builds these values is covered by [ApipostOptionsPanelTest],
 * which needs the IntelliJ fixture for Swing initialization.
 */
class ApipostConfigTest {

    @Test
    fun `config defaults to no overrides`() {
        val cfg = ApipostConfig()
        assertEquals(null, cfg.selectedToken)
        assertEquals(null, cfg.projectId)
    }

    @Test
    fun `config is a ChannelConfig subtype`() {
        val cfg: ChannelConfig = ApipostConfig(selectedToken = "t", projectId = "p")
        val typed = cfg as ApipostConfig
        assertEquals("t", typed.selectedToken)
        assertEquals("p", typed.projectId)
    }

    @Test
    fun `config has exactly selectedToken and projectId`() {
        assertEquals(
            listOf("projectId", "selectedToken"),
            ApipostConfig::class.memberProperties.map { it.name }.sorted(),
        )
    }

    @Test
    fun `config has no outputDir fileName or exportMode`() {
        // These were deliberately removed: outputDir/fileName belong to
        // ChannelConfig.FileConfig (final data class — cannot be extended),
        // and exportMode died with the whole-document push.
        val names = ApipostConfig::class.memberProperties.map { it.name }
        assertTrue("outputDir should not exist on ApipostConfig", "outputDir" !in names)
        assertTrue("fileName should not exist on ApipostConfig", "fileName" !in names)
        assertTrue("exportMode should not exist on ApipostConfig", "exportMode" !in names)
    }

}
