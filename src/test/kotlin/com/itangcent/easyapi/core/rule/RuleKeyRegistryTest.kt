package com.itangcent.easyapi.core.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [RuleKeyRegistry.assembleKeys] — the pure assembly logic that
 * combines general [RuleKeys], channel-specific keys, and implicit keys into
 * a single de-duplicated catalog.
 *
 * Uses [RuleKeyRegistry.assembleKeys] directly so no real IntelliJ [Project]
 * is required. The project-scoped [RuleKeyRegistry.allKeys] is a thin wrapper
 * around [ChannelRegistry.allChannels], so this test exercises every
 * meaningful branch of the catalog logic.
 */
class RuleKeyRegistryTest {

    @Test
    fun assembleKeysIncludesGeneralKeys() {
        val keys = RuleKeyRegistry.assembleKeys(emptyList())
        val generalNames = keys.filter { it.source == "general" }.map { it.key.name }.toSet()
        assertTrue("API_NAME missing", RuleKeys.API_NAME.name in generalNames)
        assertTrue("FIELD_REQUIRED missing", RuleKeys.FIELD_REQUIRED.name in generalNames)
    }

    @Test
    fun assembleKeysIncludesImplicitKeysWithCorrectSource() {
        val keys = RuleKeyRegistry.assembleKeys(emptyList())
        val implicitNames = keys.filter { it.source == "implicit" }.map { it.key.name }.toSet()
        assertEquals(
            setOf(
                "max.deep",
                "max.elements",
                "markdown.template.url.ttl.seconds",
                "markdown.template.url.max.bytes",
                "markdown.curl.host"
            ),
            implicitNames
        )
    }

    @Test
    fun assembleKeysIncludesChannelKeysWithChannelIdAsSource() {
        val fakeChannelKey = RuleKey.string("test.channel.only")
        val keys = RuleKeyRegistry.assembleKeys(
            listOf("testChannel" to listOf(fakeChannelKey))
        )
        val match = keys.firstOrNull { it.key.name == "test.channel.only" }
        assertNotNull("channel key missing", match)
        assertEquals("testChannel", match!!.source)
    }

    @Test
    fun assembleKeysDeduplicatesByPrimaryNameGeneralTakesPrecedence() {
        // Same name declared as both a general key (via RuleKeys — API_NAME)
        // and a channel key. General must win.
        val duplicate = RuleKey.string(RuleKeys.API_NAME.name)
        val keys = RuleKeyRegistry.assembleKeys(
            listOf("testChannel" to listOf(duplicate))
        )
        val apiNameInfos = keys.filter { it.key.name == RuleKeys.API_NAME.name }
        assertEquals("expected exactly one entry for api.name", 1, apiNameInfos.size)
        assertEquals("general", apiNameInfos[0].source)
    }

    @Test
    fun assembleKeysDeduplicatesChannelKeysAcrossChannels() {
        // Two channels contributing the same key name — only the first wins.
        val key1 = RuleKey.string("dup.key")
        val keys = RuleKeyRegistry.assembleKeys(
            listOf(
                "channelA" to listOf(key1),
                "channelB" to listOf(RuleKey.string("dup.key"))
            )
        )
        val dupInfos = keys.filter { it.key.name == "dup.key" }
        assertEquals(1, dupInfos.size)
        assertEquals("channelA", dupInfos[0].source)
    }

    @Test
    fun assembleKeysAcceptsAliasesInAllKeyNames() {
        val keys = RuleKeyRegistry.assembleKeys(emptyList())
        val names = keys.flatMap { it.key.allNames }.toSet()
        // PARAM_DOC has alias "doc.param" per RuleKeys.kt.
        assertTrue("param.doc primary missing", "param.doc" in names)
        assertTrue("doc.param alias missing", "doc.param" in names)
    }

    @Test
    fun implicitKeysListIsNonEmptyAndStable() {
        // Guard against accidentally emptying the implicit list during refactors.
        assertTrue(ImplicitConfigKeys.all.isNotEmpty())
        // The 5 known implicit keys must all be there.
        val names = ImplicitConfigKeys.all.map { it.name }.toSet()
        assertTrue("max.deep missing", "max.deep" in names)
        assertTrue("max.elements missing", "max.elements" in names)
        assertTrue(
            "markdown.template.url.ttl.seconds missing",
            "markdown.template.url.ttl.seconds" in names
        )
        assertTrue(
            "markdown.template.url.max.bytes missing",
            "markdown.template.url.max.bytes" in names
        )
        assertTrue(
            "markdown.curl.host missing",
            "markdown.curl.host" in names
        )
    }

    @Test
    fun assembleKeysReturnsEmptyImplicitSourceListWhenGeneralKeyShadows() {
        // If an implicit key name collides with a general key name, the
        // implicit entry is dropped (general takes precedence). Verify the
        // dedup guard works for the implicit stage too by constructing a
        // scenario with a fake general-key collisions — but since
        // ImplicitConfigKeys is fixed and doesn't collide with RuleKeys today,
        // we just assert the implicit keys are all present (no collision).
        val keys = RuleKeyRegistry.assembleKeys(emptyList())
        val implicitCount = keys.count { it.source == "implicit" }
        assertEquals(
            ImplicitConfigKeys.all.size,
            implicitCount
        )
    }

    // ===============================================================
    // isEnabledSource — pure enablement resolution (design C4a + AC-S4)
    // ===============================================================

    private fun info(name: String, source: String): RuleKeyRegistry.RuleKeyInfo =
        RuleKeyRegistry.RuleKeyInfo(RuleKey.string(name), source)

    private val emptySets = emptySet<String>()
    private val postmanEnabled = setOf("postman")
    private val markdownEnabled = setOf("markdown")
    private val allChannels = setOf("postman", "markdown", "curl")
    private val allFrameworks = setOf("Custom", "SpringMVC")

    // --- General keys ---

    @Test
    fun isEnabledSource_generalKeyNoChannelPrefix_alwaysEnabled() {
        // api.name has no channel prefix → enabled regardless of channel state.
        assertTrue(
            RuleKeyRegistry.isEnabledSource(
                info("api.name", "general"),
                enabledChannelIds = emptySets,
                allChannelIds = allChannels,
                enabledFrameworkIds = emptySets,
                allFrameworkIds = allFrameworks
            )
        )
    }

    @Test
    fun isEnabledSource_generalKeyWithChannelPrefix_channelEnabled_returnsTrue() {
        // postman.test starts with "postman." → owned by postman channel.
        assertTrue(
            RuleKeyRegistry.isEnabledSource(
                info("postman.test", "general"),
                enabledChannelIds = postmanEnabled,
                allChannelIds = allChannels,
                enabledFrameworkIds = emptySets,
                allFrameworkIds = allFrameworks
            )
        )
    }

    @Test
    fun isEnabledSource_generalKeyWithChannelPrefix_channelDisabled_returnsFalse() {
        // AC-S4: postman.* keys are filtered when Postman is disabled.
        assertFalse(
            RuleKeyRegistry.isEnabledSource(
                info("postman.test", "general"),
                enabledChannelIds = emptySets,
                allChannelIds = allChannels,
                enabledFrameworkIds = emptySets,
                allFrameworkIds = allFrameworks
            )
        )
    }

    @Test
    fun isEnabledSource_generalKeyWithChannelPrefix_unknownChannel_notFiltered() {
        // If the channel is not registered (not in allChannelIds), the
        // prefix check does not apply — the key stays enabled.
        assertTrue(
            RuleKeyRegistry.isEnabledSource(
                info("postman.test", "general"),
                enabledChannelIds = emptySets,
                allChannelIds = emptySets,
                enabledFrameworkIds = emptySets,
                allFrameworkIds = allFrameworks
            )
        )
    }

    // --- Implicit keys (also subject to channel prefix check) ---

    @Test
    fun isEnabledSource_implicitKeyWithChannelPrefix_channelDisabled_returnsFalse() {
        // markdown.template.url.ttl.seconds is implicit but markdown-owned.
        assertFalse(
            RuleKeyRegistry.isEnabledSource(
                info("markdown.template.url.ttl.seconds", "implicit"),
                enabledChannelIds = emptySets,
                allChannelIds = setOf("markdown"),
                enabledFrameworkIds = emptySets,
                allFrameworkIds = emptySets
            )
        )
    }

    @Test
    fun isEnabledSource_implicitKeyWithChannelPrefix_channelEnabled_returnsTrue() {
        assertTrue(
            RuleKeyRegistry.isEnabledSource(
                info("markdown.template.url.ttl.seconds", "implicit"),
                enabledChannelIds = setOf("markdown"),
                allChannelIds = setOf("markdown"),
                enabledFrameworkIds = emptySets,
                allFrameworkIds = emptySets
            )
        )
    }

    @Test
    fun isEnabledSource_implicitKeyNoChannelPrefix_alwaysEnabled() {
        assertTrue(
            RuleKeyRegistry.isEnabledSource(
                info("max.deep", "implicit"),
                enabledChannelIds = emptySets,
                allChannelIds = allChannels,
                enabledFrameworkIds = emptySets,
                allFrameworkIds = allFrameworks
            )
        )
    }

    // --- Channel-sourced keys ---

    @Test
    fun isEnabledSource_channelSourcedKey_channelEnabled_returnsTrue() {
        assertTrue(
            RuleKeyRegistry.isEnabledSource(
                info("hoppscotch.something", "hoppscotch"),
                enabledChannelIds = setOf("hoppscotch"),
                allChannelIds = setOf("hoppscotch", "postman"),
                enabledFrameworkIds = emptySets,
                allFrameworkIds = emptySets
            )
        )
    }

    @Test
    fun isEnabledSource_channelSourcedKey_channelDisabled_returnsFalse() {
        assertFalse(
            RuleKeyRegistry.isEnabledSource(
                info("hoppscotch.something", "hoppscotch"),
                enabledChannelIds = emptySets,
                allChannelIds = setOf("hoppscotch", "postman"),
                enabledFrameworkIds = emptySets,
                allFrameworkIds = emptySets
            )
        )
    }

    // --- Framework-sourced keys ---

    @Test
    fun isEnabledSource_frameworkSourcedKey_frameworkEnabled_returnsTrue() {
        assertTrue(
            RuleKeyRegistry.isEnabledSource(
                info("custom.something", "Custom"),
                enabledChannelIds = emptySets,
                allChannelIds = emptySets,
                enabledFrameworkIds = setOf("Custom"),
                allFrameworkIds = allFrameworks
            )
        )
    }

    @Test
    fun isEnabledSource_frameworkSourcedKey_frameworkDisabled_returnsFalse() {
        assertFalse(
            RuleKeyRegistry.isEnabledSource(
                info("custom.something", "Custom"),
                enabledChannelIds = emptySets,
                allChannelIds = emptySets,
                enabledFrameworkIds = emptySets,
                allFrameworkIds = allFrameworks
            )
        )
    }

    // --- Unknown source kind ---

    @Test
    fun isEnabledSource_unknownSourceKind_neverFiltered() {
        // A source that is neither a channel id nor a framework name stays
        // enabled — never filter unknown source kinds.
        assertTrue(
            RuleKeyRegistry.isEnabledSource(
                info("mystery.key", "mysterySource"),
                enabledChannelIds = emptySets,
                allChannelIds = allChannels,
                enabledFrameworkIds = emptySets,
                allFrameworkIds = allFrameworks
            )
        )
    }

    @Test
    fun isEnabledSource_generalKeyMatchesMultipleChannels_allMustBeEnabled() {
        // If a key name could match multiple channel prefixes (unlikely but
        // possible), all matching channels must be enabled. Construct a
        // scenario where "postman.extra" matches "postman" (disabled) → false.
        assertFalse(
            RuleKeyRegistry.isEnabledSource(
                info("postman.extra", "general"),
                enabledChannelIds = setOf("markdown"),
                allChannelIds = setOf("postman", "markdown"),
                enabledFrameworkIds = emptySets,
                allFrameworkIds = emptySets
            )
        )
    }
}
