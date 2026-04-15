package com.itangcent.easyapi.extension

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

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
        println("Loaded extensions: ${extensions.map { it.code }}")
    }

    @Test
    fun testYapiExtensionExists() {
        val extensions = ExtensionConfigRegistry.allExtensions()
        val codes = extensions.map { it.code }
        println("All extension codes: $codes")
        assertTrue("yapi extension should exist. Available: $codes", codes.contains("yapi"))
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
}
