package com.itangcent.easyapi.rule.parser

import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBus
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class ScriptConfigWrapperTest {

    private lateinit var wrapper: ScriptConfigWrapper
    private lateinit var mockProject: Project
    private lateinit var mockMessageBus: MessageBus

    @Before
    fun setUp() {
        mockProject = mock(Project::class.java)
        mockMessageBus = mock(MessageBus::class.java)
        `when`(mockProject.messageBus).thenReturn(mockMessageBus)

        val configReader = TestConfigReader.fromRules(
            mockProject,
            "api.name" to "getUser",
            "api.name" to "listUsers",
            "api.tag" to "user",
            "base.url" to "http://localhost:8080",
            "app.name" to "MyApp"
        )
        wrapper = ScriptConfigWrapper(configReader)
    }

    @Test
    fun testGet() {
        assertEquals("getUser", wrapper.get("api.name"))
        assertEquals("user", wrapper.get("api.tag"))
        assertNull(wrapper.get("nonexistent"))
    }

    @Test
    fun testGetValues() {
        assertEquals(listOf("getUser", "listUsers"), wrapper.getValues("api.name"))
        assertEquals(listOf("user"), wrapper.getValues("api.tag"))
        assertEquals(emptyList<String>(), wrapper.getValues("nonexistent"))
    }

    @Test
    fun testResolveProperty_withPlaceholders() {
        val result = wrapper.resolveProperty("\${base.url}/api")
        assertEquals("http://localhost:8080/api", result)
    }

    @Test
    fun testResolveProperty_multiplePlaceholders() {
        val result = wrapper.resolveProperty("\${app.name} at \${base.url}")
        assertEquals("MyApp at http://localhost:8080", result)
    }

    @Test
    fun testResolveProperty_unknownPlaceholder() {
        val result = wrapper.resolveProperty("\${unknown.key}/path")
        assertEquals("\${unknown.key}/path", result)
    }

    @Test
    fun testResolveProperty_noPlaceholders() {
        val result = wrapper.resolveProperty("plain text")
        assertEquals("plain text", result)
    }
}
