package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.config.ConfigReader
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScriptConfigWrapperTest {

    private lateinit var wrapper: ScriptConfigWrapper

    @Before
    fun setUp() {
        val configReader = object : ConfigReader {
            private val data = mapOf(
                "api.name" to listOf("getUser", "listUsers"),
                "api.tag" to listOf("user"),
                "base.url" to listOf("http://localhost:8080"),
                "app.name" to listOf("MyApp")
            )

            override fun getFirst(key: String): String? = data[key]?.firstOrNull()
            override fun getAll(key: String): List<String> = data[key] ?: emptyList()
            override suspend fun reload() {}
            override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {
                data.forEach { (k, v) -> if (keyFilter(k)) v.forEach { action(k, it) } }
            }
        }
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
