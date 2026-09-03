package com.itangcent.easyapi.tooling

import com.itangcent.easyapi.channel.hoppscotch.HoppscotchRuleKeys
import com.itangcent.easyapi.channel.openapi.OpenApiRuleKeys
import com.itangcent.easyapi.channel.postman.PostmanRuleKeys
import com.itangcent.easyapi.channel.yapi.YapiMetaRuleKeys
import com.itangcent.easyapi.channel.yapi.YapiRuleKeys
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.RuleKeyRegistry
import com.itangcent.easyapi.framework.custom.CustomApiRecognizer
import com.itangcent.easyapi.framework.custom.CustomRuleKeys
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests (no IntelliJ [Project]) for [RuleKeySchemeExporter].
 *
 * These pin two properties of the auto-exported catalog that the external
 * `easy-yapi-assistant` skill relies on:
 * 1. **Parity with the runtime catalog** — `collectKeys()` yields exactly the
 *    `(name, source)` set that [RuleKeyRegistry.assembleKeys] produces for the
 *    current set of channel/framework rule-key sources.
 * 2. **Scheme completeness** — every exported key is self-describing, so the
 *    external skill gets the same scheme info the built-in agent sees via
 *    `list_rule_keys` (not a name-only snapshot).
 */
class RuleKeySchemeExporterTest {

    private companion object {
        /** The channel/framework sources currently registered in [RuleKeyCatalog.SOURCES]. */
        val KNOWN_CHANNELS = listOf(
            "postman" to { RuleKey.collectFrom(PostmanRuleKeys) },
            "hoppscotch" to { RuleKey.collectFrom(HoppscotchRuleKeys) },
            "openapi" to { RuleKey.collectFrom(OpenApiRuleKeys) },
            "yapi" to { RuleKey.collectFrom(YapiRuleKeys) + RuleKey.collectFrom(YapiMetaRuleKeys) }
        )
        val KNOWN_FRAMEWORKS = listOf(
            CustomApiRecognizer.FRAMEWORK_NAME to { RuleKey.collectFrom(CustomRuleKeys) }
        )

        fun namesBySource(keys: List<com.itangcent.easyapi.core.rule.RuleKeyCatalog.SchemeEntry>): Map<String, String> =
            keys.associate { it.name to it.source }
    }

    @Test
    fun testExporterMatchesRuleKeyRegistryAssembly() {
        val expected = RuleKeyRegistry.assembleKeys(
            channelKeys = KNOWN_CHANNELS.map { (src, sup) -> src to sup() },
            frameworkKeys = KNOWN_FRAMEWORKS.map { (src, sup) -> src to sup() }
        ).map { it.key.name to it.source }.toMap()

        val actual = namesBySource(RuleKeySchemeExporter.collectKeys())

        assertEquals(
            "exporter.collectKeys() must resolve to the same (name, source) catalog as " +
                "RuleKeyRegistry.assembleKeys; a new channel/framework source must be registered " +
                "in RuleKeyCatalog.SOURCES",
            expected, actual
        )
    }

    @Test
    fun testCollectKeysDeDuplicatesByPrimaryName() {
        val keys = RuleKeySchemeExporter.collectKeys()
        val names = keys.map { it.name }
        assertEquals("primary names must be unique", names.size, names.toSet().size)
        val sorted = keys.map { it.name }
        assertEquals("catalog must be sorted by name", sorted.sorted(), sorted)
    }

    @Test
    fun testEveryKeyIsSelfDescribing() {
        val keys = RuleKeySchemeExporter.collectKeys()
        assertTrue("catalog must not be empty", keys.isNotEmpty())
        keys.forEach { k ->
            assertTrue(
                "key ${k.name} must carry a non-blank summary (self-describing scheme); " +
                    "add a RuleKeyScheme.summary at declaration",
                !k.summary.isNullOrBlank()
            )
            assertTrue(
                "key ${k.name} must declare outputShape or contextKinds so the external skill " +
                    "learns the expected shape, not just the name",
                k.outputShape != null || k.contextKinds.isNotEmpty() || k.staticConfiguration
            )
        }
    }

    @Test
    fun testJsonIsParseableAndListsEveryKey() {
        val keys = RuleKeySchemeExporter.collectKeys()
        val json = RuleKeySchemeExporter.toJson(keys)
        val parsed = JsonParser.parseString(json)
        assertTrue("toJson must produce a JSON array", parsed.isJsonArray)
        val names = parsed.asJsonArray.map { it.asJsonObject.get("name").asString }.toSet()
        assertEquals("json must list every exported key name", keys.map { it.name }.toSet(), names)
    }

    /**
     * D4.3 parity guard: the JSON entry emitted by the exporter must carry
     * *exactly* the field set declared on [com.itangcent.easyapi.core.rule.RuleKeyCatalog.SchemeEntry].
     * Because [com.itangcent.easyapi.core.ai.tools.ListRuleKeysTool] renders entries
     * from the *same* [SchemeEntry] type, this pins the external `rule-keys.json`
     * shape to the internal `list_rule_keys` shape — a new field added to
     * [SchemeEntry] must be reflected in [RuleKeySchemeExporter.toJson] or this
     * guard fails (and vice versa).
     */
    @Test
    fun testJsonEntryFieldSetMatchesSchemeEntry() {
        val keys = RuleKeySchemeExporter.collectKeys()
        assertTrue("need at least one key to inspect the JSON shape", keys.isNotEmpty())
        val json = RuleKeySchemeExporter.toJson(keys)
        val parsed = JsonParser.parseString(json).asJsonArray
        val entryFields = parsed[0].asJsonObject.keySet()

        // The canonical field set of SchemeEntry, rendered by toJson.
        val expectedFields = setOf(
            "name", "aliases", "type", "source", "mode",
            "contextKinds", "outputShape", "additionalBindings", "summary",
            "staticConfiguration", "dryRunnable", "jsonValue", "notes"
        )

        assertEquals(
            "rule-keys.json entry fields must match SchemeEntry exactly (D4.3 parity); " +
                "a field added to/removed from SchemeEntry must be reflected in toJson",
            expectedFields,
            entryFields
        )
    }
}