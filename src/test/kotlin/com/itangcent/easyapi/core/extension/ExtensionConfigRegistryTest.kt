package com.itangcent.easyapi.core.extension

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ExtensionConfigRegistryTest {

    @Before
    fun setUp() {
        ExtensionConfigRegistry.loadExtensions()
    }

    @Test
    fun testAllExtensions_notEmpty() {
        val extensions = ExtensionConfigRegistry.allExtensions()
        assertNotNull(extensions)
        assertTrue("Expected at least one extension", extensions.isNotEmpty())
    }

    @Test
    fun testGetExtension_existing() {
        val extensions = ExtensionConfigRegistry.allExtensions()
        if (extensions.isNotEmpty()) {
            val firstCode = extensions.first().code
            val extension = ExtensionConfigRegistry.getExtension(firstCode)
            assertNotNull(extension)
            assertEquals(firstCode, extension!!.code)
        }
    }

    @Test
    fun testGetExtension_nonExisting() {
        val extension = ExtensionConfigRegistry.getExtension("non_existing_extension_xyz")
        assertNull(extension)
    }

    @Test
    fun testCodes_returnsAllCodes() {
        val extensions = ExtensionConfigRegistry.allExtensions()
        val codes = ExtensionConfigRegistry.codes()
        assertEquals(extensions.size, codes.size)
        extensions.forEach { extension ->
            assertTrue(codes.contains(extension.code))
        }
    }

    @Test
    fun testDefaultCodes_onlyDefaultEnabled() {
        val defaultCodes = ExtensionConfigRegistry.defaultCodes()
        val defaultExtensions = ExtensionConfigRegistry.allExtensions().filter { it.defaultEnabled }
        if (defaultExtensions.isEmpty()) {
            assertTrue(defaultCodes.isEmpty())
        } else {
            defaultExtensions.forEach { extension ->
                assertTrue("Expected ${extension.code} in default codes", defaultCodes.contains(extension.code))
            }
        }
    }

    @Test
    fun testBuildConfig_withSpecificCode() {
        val extensions = ExtensionConfigRegistry.allExtensions()
        if (extensions.isNotEmpty()) {
            val extension = extensions.first()
            val config = ExtensionConfigRegistry.buildConfig(arrayOf(extension.code))
            if (extension.content.isNotEmpty()) {
                assertTrue(config.contains(extension.content))
            }
        }
    }

    @Test
    fun testBuildConfig_withEmptyCodes() {
        val config = ExtensionConfigRegistry.buildConfig(emptyArray())
        assertNotNull(config)
    }

    @Test
    fun testSelectedCodes_withSpecificCode() {
        val extensions = ExtensionConfigRegistry.allExtensions()
        if (extensions.isNotEmpty()) {
            val extension = extensions.first()
            val selected = ExtensionConfigRegistry.selectedCodes(arrayOf(extension.code))
            assertTrue(selected.contains(extension.code))
        }
    }

    @Test
    fun testAddSelectedConfig() {
        val result = ExtensionConfigRegistry.addSelectedConfig(arrayOf("spring", "mvc"), "jaxrs")
        assertTrue(result.contains("spring"))
        assertTrue(result.contains("mvc"))
        assertTrue(result.contains("jaxrs"))
    }

    @Test
    fun testAddSelectedConfig_removesNegation() {
        val result = ExtensionConfigRegistry.addSelectedConfig(arrayOf("spring", "-jaxrs"), "jaxrs")
        assertTrue(result.contains("jaxrs"))
        assertFalse(result.contains("-jaxrs"))
    }

    @Test
    fun testRemoveSelectedConfig() {
        val result = ExtensionConfigRegistry.removeSelectedConfig(arrayOf("spring", "mvc", "jaxrs"), "jaxrs")
        assertTrue(result.contains("spring"))
        assertTrue(result.contains("mvc"))
        assertFalse(result.contains("jaxrs"))
        assertTrue(result.contains("-jaxrs"))
    }

    @Test
    fun testRemoveSelectedConfig_addsNegation() {
        val result = ExtensionConfigRegistry.removeSelectedConfig(arrayOf("spring"), "jaxrs")
        assertTrue(result.contains("-jaxrs"))
    }

    @Test
    fun testCodesToString() {
        val codes = arrayOf("spring", "mvc", "jaxrs")
        val str = ExtensionConfigRegistry.codesToString(codes)
        assertEquals("spring,mvc,jaxrs", str)
    }

    /**
     * Characterisation of the #1461 root cause: a positive-only code list cannot
     * express a deselection, because the reader falls back to `defaultEnabled` for
     * any code it does not find. This is why `RuleFileSettings.updateExtensionCodes`
     * has to emit `-<code>` exclusions.
     */
    @Test
    fun testSelectedCodes_positiveOnlyList_cannotExpressDeselection() {
        val defaultCode = ExtensionConfigRegistry.defaultCodes().first()
        val positiveOnly = ExtensionConfigRegistry.defaultCodes().filter { it != defaultCode }

        val enabled = ExtensionConfigRegistry.selectedCodes(positiveOnly.toTypedArray())

        assertTrue(
            "a positive-only list silently re-enables '$defaultCode' — the #1461 bug",
            enabled.contains(defaultCode)
        )
    }

    /**
     * [ExtensionConfigRegistry.enabledExtensions] is the single grammar decision;
     * every other query has to be a projection of it, in every code-list shape —
     * empty, positive, negative, mixed, and a code listed both ways (positive wins).
     */
    @Test
    fun testEnabledExtensions_isTheOnlyGrammarDecision() {
        val defaultCode = ExtensionConfigRegistry.defaultCodes().first()
        val codeLists: List<Array<String>> = listOf(
            emptyArray(),
            arrayOf(defaultCode),
            arrayOf("-$defaultCode"),
            ExtensionConfigRegistry.defaultCodes(),
            ExtensionConfigRegistry.codes(),
            arrayOf(defaultCode, "-$defaultCode")
        )

        codeLists.forEach { codes ->
            assertArrayEquals(
                "selectedCodes must be enabledExtensions projected onto codes, for ${codes.toList()}",
                ExtensionConfigRegistry.enabledExtensions(codes).map { it.code }.toTypedArray(),
                ExtensionConfigRegistry.selectedCodes(codes)
            )
        }
    }

    /**
     * "An empty code list reads as the defaults" is a consequence of the grammar, not
     * a special case of it: with an empty list both OR branches collapse to
     * `defaultEnabled`. Pinned so the branch that used to spell this out separately
     * cannot quietly come back.
     */
    @Test
    fun testBuildConfig_emptyListReadsAsDefaults() {
        assertEquals(
            ExtensionConfigRegistry.buildConfig(ExtensionConfigRegistry.defaultCodes()),
            ExtensionConfigRegistry.buildConfig(emptyArray())
        )
    }

    /**
     * The grammar owner's two halves have to stay inverse: a selection that turned a
     * default-enabled extension off must survive being persisted and read back.
     */
    @Test
    fun testEncodeAndDecodeAreInverse() {
        val defaultCode = ExtensionConfigRegistry.defaultCodes().first()
        val checked = ExtensionConfigRegistry.defaultCodes().filter { it != defaultCode }

        val encoded = ExtensionConfigRegistry.encodeSelection(checked)

        assertTrue("'$defaultCode' must be written as an exclusion, got: $encoded", encoded.contains("-$defaultCode"))
        assertEquals(
            checked.toSet(),
            ExtensionConfigRegistry
                .selectedCodes(ExtensionConfigRegistry.stringToCodes(encoded))
                .toSet()
        )
    }

    @Test
    fun testStringToCodes() {
        val str = "spring,mvc,jaxrs"
        val codes = ExtensionConfigRegistry.stringToCodes(str)
        assertEquals(3, codes.size)
        assertTrue(codes.contains("spring"))
        assertTrue(codes.contains("mvc"))
        assertTrue(codes.contains("jaxrs"))
    }

    @Test
    fun testStringToCodes_withSpaces() {
        val str = "spring , mvc , jaxrs "
        val codes = ExtensionConfigRegistry.stringToCodes(str)
        assertEquals(3, codes.size)
        assertTrue(codes.contains("spring"))
        assertTrue(codes.contains("mvc"))
        assertTrue(codes.contains("jaxrs"))
    }

    @Test
    fun testCodesToString_emptyArray() {
        val codes = emptyArray<String>()
        val str = ExtensionConfigRegistry.codesToString(codes)
        assertEquals("", str)
    }

    @Test
    fun testStringToCodes_emptyString() {
        val codes = ExtensionConfigRegistry.stringToCodes("")
        assertTrue(codes.isEmpty())
    }

    @Test
    fun testLoadExtensionsWithNullProjectLoadsBaseConfigs() {
        ExtensionConfigRegistry.loadExtensions(null)
        val extensions = ExtensionConfigRegistry.allExtensions()
        assertNotNull(extensions)
        assertTrue("Expected base extensions with null project", extensions.isNotEmpty())
    }

    @Test
    fun testLoadExtensionsWithProjectResolvesChannelConfigs() {
        val project = mock<Project>()
        val channelRegistry = mock<ChannelRegistry>()
        whenever(project.getService(ChannelRegistry::class.java)).thenReturn(channelRegistry)
        whenever(channelRegistry.configFiles()).thenReturn(listOf("custom-channel"))

        ExtensionConfigRegistry.loadExtensions(project)
        val extensions = ExtensionConfigRegistry.allExtensions()
        assertNotNull(extensions)
        assertTrue(extensions.isNotEmpty())
    }

    @Test
    fun testLoadExtensionsWithProjectHandlesChannelRegistryFailure() {
        val project = mock<Project>()
        whenever(project.getService(ChannelRegistry::class.java)).thenReturn(null)

        ExtensionConfigRegistry.loadExtensions(project)
        val extensions = ExtensionConfigRegistry.allExtensions()
        assertNotNull(extensions)
        assertTrue(extensions.isNotEmpty())
    }

    @Test
    fun testBuildConfigWithNegationExcludesDefaultEnabled() {
        val extensions = ExtensionConfigRegistry.allExtensions()
        val defaultEnabled = extensions.firstOrNull { it.defaultEnabled } ?: return
        val config = ExtensionConfigRegistry.buildConfig(arrayOf("-${defaultEnabled.code}"))
        assertFalse(
            "Negated default-enabled extension '${defaultEnabled.code}' should be excluded",
            config.contains(defaultEnabled.content)
        )
    }

    @Test
    fun testBuildConfigWithExplicitCodeAndNegation() {
        val extensions = ExtensionConfigRegistry.allExtensions()
        val defaultEnabled = extensions.first { it.defaultEnabled }
        val disabled = extensions.firstOrNull { !it.defaultEnabled } ?: return
        val config = ExtensionConfigRegistry.buildConfig(arrayOf(disabled.code, "-${defaultEnabled.code}"))
        assertTrue("Explicitly selected '${disabled.code}' should be included", config.contains(disabled.content))
        assertFalse("Negated '${defaultEnabled.code}' should be excluded", config.contains(defaultEnabled.content))
    }

    @Test
    fun testBuildConfigWithCustomSeparator() {
        val extensions = ExtensionConfigRegistry.allExtensions()
        if (extensions.isNotEmpty()) {
            val extension = extensions.first()
            val config = ExtensionConfigRegistry.buildConfig(arrayOf(extension.code), separator = "|")
            assertNotNull(config)
        }
    }

    @Test
    fun testSelectedCodesWithNegationExcludesDefaultEnabled() {
        val extensions = ExtensionConfigRegistry.allExtensions()
        val defaultEnabled = extensions.firstOrNull { it.defaultEnabled } ?: return
        val selected = ExtensionConfigRegistry.selectedCodes(arrayOf("-${defaultEnabled.code}"))
        assertFalse(
            "Negated default-enabled '${defaultEnabled.code}' should not be selected",
            selected.contains(defaultEnabled.code)
        )
    }

    @Test
    fun testSelectedCodesReturnsAllDefaultEnabledWhenEmpty() {
        val selected = ExtensionConfigRegistry.selectedCodes(emptyArray())
        val defaultExtensions = ExtensionConfigRegistry.allExtensions().filter { it.defaultEnabled }
        assertEquals(defaultExtensions.size, selected.size)
        defaultExtensions.forEach { ext ->
            assertTrue("Default-enabled '${ext.code}' should be selected", selected.contains(ext.code))
        }
    }

    @Test
    fun testAddSelectedConfigTrimsCodes() {
        val result = ExtensionConfigRegistry.addSelectedConfig(arrayOf("spring"), "  jaxrs  ")
        assertTrue(result.contains("jaxrs"))
        assertFalse(result.contains("  jaxrs  "))
    }

    @Test
    fun testAddSelectedConfigFiltersBlanks() {
        val result = ExtensionConfigRegistry.addSelectedConfig(arrayOf("spring", ""), "jaxrs")
        assertFalse(result.any { it.isBlank() })
        assertTrue(result.contains("spring"))
        assertTrue(result.contains("jaxrs"))
    }

    @Test
    fun testRemoveSelectedConfigRemovesMultipleCodes() {
        val result = ExtensionConfigRegistry.removeSelectedConfig(arrayOf("spring", "mvc", "jaxrs"), "mvc", "jaxrs")
        assertTrue(result.contains("spring"))
        assertFalse(result.contains("mvc"))
        assertFalse(result.contains("jaxrs"))
        assertTrue(result.contains("-mvc"))
        assertTrue(result.contains("-jaxrs"))
    }

    @Test
    fun testRemoveSelectedConfigTrimsCodes() {
        val result = ExtensionConfigRegistry.removeSelectedConfig(arrayOf("spring", "mvc"), "  mvc  ")
        assertFalse(result.contains("mvc"))
        assertTrue(result.contains("-mvc"))
    }

    @Test
    fun testCodesToStringFiltersBlanks() {
        val codes = arrayOf("spring", "", "  ", "jaxrs")
        val str = ExtensionConfigRegistry.codesToString(codes)
        assertEquals("spring,jaxrs", str)
    }

    @Test
    fun testStringToCodesWithExtraCommas() {
        val codes = ExtensionConfigRegistry.stringToCodes("spring,,mvc,,,jaxrs")
        assertEquals(3, codes.size)
        assertTrue(codes.contains("spring"))
        assertTrue(codes.contains("mvc"))
        assertTrue(codes.contains("jaxrs"))
    }

    @Test
    fun testStringToCodesWithOnlyCommas() {
        val codes = ExtensionConfigRegistry.stringToCodes(",,,")
        assertTrue(codes.isEmpty())
    }

    @Test
    fun testCodesToStringRoundTripsWithStringToCodes() {
        val original = arrayOf("spring", "mvc", "jaxrs")
        val str = ExtensionConfigRegistry.codesToString(original)
        val roundTripped = ExtensionConfigRegistry.stringToCodes(str)
        assertArrayEquals(original, roundTripped)
    }
}
