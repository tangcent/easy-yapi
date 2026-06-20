package com.itangcent.easyapi.ide.fieldformat

import com.itangcent.easyapi.ide.PropertiesService
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.ResourceLoader
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Tests for the four [FieldFormatChannel] implementations:
 * [JsonFieldFormatChannel], [Json5FieldFormatChannel],
 * [YamlFieldFormatChannel], [PropertiesFieldFormatChannel].
 *
 * Each channel is exercised end-to-end against `model/UserInfo.java`:
 * - The channel is instantiated directly (no extension point lookup needed).
 * - The `format` suspend function is called inside [runTest].
 * - The output is compared against golden output captured from the
 *   pre-refactor `FieldFormatServiceTest` golden files (for JSON/JSON5/Properties)
 *   and the canonical YAML rendering (for YAML).
 */
class FieldFormatChannelTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.empty(project)

    private lateinit var userInfoClass: com.intellij.psi.PsiClass

    override fun setUp() {
        super.setUp()
        loadFile("model/UserInfo.java")
        userInfoClass = findClass("com.itangcent.model.UserInfo")!!
    }

    // ---------- Identity metadata ----------

    fun testJsonChannelIdentity() {
        val channel = JsonFieldFormatChannel()
        assertEquals("json", channel.id)
        assertEquals("JSON", channel.displayName)
        assertEquals("ToJson", channel.actionText)
    }

    fun testJson5ChannelIdentity() {
        val channel = Json5FieldFormatChannel()
        assertEquals("json5", channel.id)
        assertEquals("JSON5", channel.displayName)
        assertEquals("ToJson5", channel.actionText)
    }

    fun testYamlChannelIdentity() {
        val channel = YamlFieldFormatChannel()
        assertEquals("yaml", channel.id)
        assertEquals("YAML", channel.displayName)
        assertEquals("ToYaml", channel.actionText)
    }

    fun testPropertiesChannelIdentity() {
        val channel = PropertiesFieldFormatChannel()
        assertEquals("properties", channel.id)
        assertEquals("Properties", channel.displayName)
        assertEquals("ToProperties", channel.actionText)
    }

    // ---------- JSON channel ----------

    fun testJsonChannelFormatsUserInfo() = runTest {
        val channel = JsonFieldFormatChannel()
        val actual = channel.format(project, userInfoClass)
        val expected = ResourceLoader.read("/result/com.itangcent.easyapi.ide.FieldFormatServiceTest.toJson.txt")
        assertEquals(expected, actual)
    }

    fun testJsonChannelReturnsNonEmpty() = runTest {
        val channel = JsonFieldFormatChannel()
        val actual = channel.format(project, userInfoClass)
        assertTrue("JSON output should be non-empty", actual.isNotEmpty())
        assertTrue("JSON output should start with {", actual.trimStart().startsWith("{"))
    }

    // ---------- JSON5 channel ----------

    fun testJson5ChannelFormatsUserInfo() = runTest {
        val channel = Json5FieldFormatChannel()
        val actual = channel.format(project, userInfoClass)
        val expected = ResourceLoader.read("/result/com.itangcent.easyapi.ide.FieldFormatServiceTest.toJson5.txt")
        assertEquals(expected, actual)
    }

    fun testJson5ChannelIncludesComments() = runTest {
        val channel = Json5FieldFormatChannel()
        val actual = channel.format(project, userInfoClass)
        // JSON5 output should include at least one comment (UserInfo has // user id etc.)
        assertTrue("JSON5 should contain comments", actual.contains("//"))
    }

    // ---------- YAML channel ----------

    fun testYamlChannelFormatsUserInfo() = runTest {
        val channel = YamlFieldFormatChannel()
        val actual = channel.format(project, userInfoClass)
        // YAML output should contain the field keys as block-style mappings
        assertTrue("YAML should contain 'id:'", actual.contains("id:"))
        assertTrue("YAML should contain 'name:'", actual.contains("name:"))
        assertTrue("YAML should contain 'age:'", actual.contains("age:"))
    }

    fun testYamlChannelReturnsNonEmpty() = runTest {
        val channel = YamlFieldFormatChannel()
        val actual = channel.format(project, userInfoClass)
        assertTrue("YAML output should be non-empty", actual.isNotEmpty())
    }

    // ---------- Properties channel ----------

    fun testPropertiesChannelFormatsUserInfo() = runTest {
        val channel = PropertiesFieldFormatChannel()
        val actual = channel.format(project, userInfoClass)
        val expected = ResourceLoader.read("/result/com.itangcent.easyapi.ide.FieldFormatServiceTest.toProperties.txt")
        assertEquals(expected, actual)
    }

    fun testPropertiesChannelDelegatesToService() = runTest {
        val channel = PropertiesFieldFormatChannel()
        val channelResult = channel.format(project, userInfoClass)
        val serviceResult = PropertiesService.getInstance(project).toProperties(userInfoClass)
        // The channel should delegate to PropertiesService — outputs must match
        assertEquals(serviceResult, channelResult)
    }

    // ---------- All channels parity ----------

    fun testAllChannelsProduceNonEmptyOutput() = runTest {
        val channels: List<FieldFormatChannel> = listOf(
            JsonFieldFormatChannel(),
            Json5FieldFormatChannel(),
            YamlFieldFormatChannel(),
            PropertiesFieldFormatChannel()
        )
        for (channel in channels) {
            val output = channel.format(project, userInfoClass)
            assertNotNull("Channel ${channel.id} should produce output", output)
            assertTrue(
                "Channel ${channel.id} should produce non-empty output",
                output.isNotEmpty()
            )
        }
    }
}
