package com.itangcent.easyapi.channel.spi

import com.itangcent.easyapi.channel.markdown.MarkdownConfig
import com.itangcent.easyapi.channel.postman.PostmanConfig
import com.itangcent.easyapi.channel.yapi.YapiConfig
import org.junit.Assert.*
import org.junit.Test

class ChannelConfigTest {

    @Test
    fun testEmptyConfig() {
        val config = ChannelConfig.Empty
        assertSame(ChannelConfig.Empty, config)
    }

    @Test
    fun testEmptyConfigIsSingleton() {
        val config1 = ChannelConfig.Empty
        val config2 = ChannelConfig.Empty
        assertSame(config1, config2)
    }

    @Test
    fun testFileConfigDefaults() {
        val config = ChannelConfig.FileConfig()
        assertNull(config.outputDir)
        assertNull(config.fileName)
    }

    @Test
    fun testFileConfigWithValues() {
        val config = ChannelConfig.FileConfig(
            outputDir = "/tmp/output",
            fileName = "api.md"
        )
        assertEquals("/tmp/output", config.outputDir)
        assertEquals("api.md", config.fileName)
    }

    @Test
    fun testFileConfigCopy() {
        val original = ChannelConfig.FileConfig(outputDir = "/tmp", fileName = "api.md")
        val modified = original.copy(fileName = "api-v2.md")
        assertEquals("/tmp", modified.outputDir)
        assertEquals("api-v2.md", modified.fileName)
        assertEquals("api.md", original.fileName)
    }

    @Test
    fun testFileConfigEquality() {
        val config1 = ChannelConfig.FileConfig(outputDir = "/tmp", fileName = "api.md")
        val config2 = ChannelConfig.FileConfig(outputDir = "/tmp", fileName = "api.md")
        assertEquals(config1, config2)
    }

    @Test
    fun testFileConfigInequality() {
        val config1 = ChannelConfig.FileConfig(outputDir = "/tmp", fileName = "api.md")
        val config2 = ChannelConfig.FileConfig(outputDir = "/tmp", fileName = "other.md")
        assertNotEquals(config1, config2)
    }

    @Test
    fun testPostmanConfigDefaults() {
        val config = PostmanConfig()
        assertNull(config.workspaceId)
        assertNull(config.workspaceName)
        assertNull(config.collectionId)
        assertNull(config.collectionName)
        assertFalse(config.isUpdate)
    }

    @Test
    fun testPostmanConfigWithValues() {
        val config = PostmanConfig(
            workspaceId = "ws-123",
            workspaceName = "My Workspace",
            collectionId = "col-456",
            collectionName = "My Collection",
            isUpdate = true
        )
        assertEquals("ws-123", config.workspaceId)
        assertEquals("My Workspace", config.workspaceName)
        assertEquals("col-456", config.collectionId)
        assertEquals("My Collection", config.collectionName)
        assertTrue(config.isUpdate)
    }

    @Test
    fun testPostmanConfigCopy() {
        val original = PostmanConfig(collectionName = "V1")
        val modified = original.copy(collectionName = "V2", isUpdate = true)
        assertEquals("V1", original.collectionName)
        assertFalse(original.isUpdate)
        assertEquals("V2", modified.collectionName)
        assertTrue(modified.isUpdate)
    }

    @Test
    fun testPostmanConfigEquality() {
        val config1 = PostmanConfig(collectionName = "Test", isUpdate = true)
        val config2 = PostmanConfig(collectionName = "Test", isUpdate = true)
        assertEquals(config1, config2)
    }

    @Test
    fun testConfigHierarchy() {
        val configs: List<ChannelConfig> = listOf(
            ChannelConfig.Empty,
            ChannelConfig.FileConfig(outputDir = "/tmp"),
            PostmanConfig(collectionName = "Test"),
            YapiConfig(selectedToken = "tok")
        )
        assertEquals(4, configs.size)
        assertTrue(configs[0] is ChannelConfig.Empty)
        assertTrue(configs[1] is ChannelConfig.FileConfig)
        assertTrue(configs[2] is PostmanConfig)
        assertTrue(configs[3] is YapiConfig)
    }

    @Test
    fun testYapiConfigDefaults() {
        val config = YapiConfig()
        assertNull(config.selectedToken)
        assertFalse(config.useCustomProject)
    }

    @Test
    fun testYapiConfigWithValues() {
        val config = YapiConfig(
            selectedToken = "tok-123",
            useCustomProject = true
        )
        assertEquals("tok-123", config.selectedToken)
        assertTrue(config.useCustomProject)
    }

    @Test
    fun testYapiConfigCopy() {
        val original = YapiConfig(selectedToken = "V1")
        val modified = original.copy(selectedToken = "V2", useCustomProject = true)
        assertEquals("V1", original.selectedToken)
        assertFalse(original.useCustomProject)
        assertEquals("V2", modified.selectedToken)
        assertTrue(modified.useCustomProject)
    }

    @Test
    fun testYapiConfigEquality() {
        val config1 = YapiConfig(selectedToken = "Test", useCustomProject = true)
        val config2 = YapiConfig(selectedToken = "Test", useCustomProject = true)
        assertEquals(config1, config2)
    }

    @Test
    fun testWhenExpression() {
        fun describe(config: ChannelConfig): String = when (config) {
            is ChannelConfig.Empty -> "empty"
            is ChannelConfig.FileConfig -> "file:${config.fileName}"
            is PostmanConfig -> "postman:${config.collectionName}"
            is YapiConfig -> "yapi:${config.selectedToken}"
            is MarkdownConfig -> "markdown:${config.fileName}"
            else -> "other"
        }

        assertEquals("empty", describe(ChannelConfig.Empty))
        assertEquals("file:api.md", describe(ChannelConfig.FileConfig(fileName = "api.md")))
        assertEquals("postman:MyAPI", describe(PostmanConfig(collectionName = "MyAPI")))
        assertEquals("yapi:tok-1", describe(YapiConfig(selectedToken = "tok-1")))
        assertEquals("markdown:api.md", describe(MarkdownConfig(fileName = "api.md")))
    }
}
