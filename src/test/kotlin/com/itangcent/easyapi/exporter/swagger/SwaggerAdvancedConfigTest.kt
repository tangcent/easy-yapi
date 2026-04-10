package com.itangcent.easyapi.exporter.swagger

import org.junit.Assert.*
import org.junit.Test

class SwaggerAdvancedConfigTest {

    private data class ParsedConfigEntry(val key: String, val value: String)

    private fun loadSwaggerAdvancedConfig(): List<ParsedConfigEntry> {
        val entries = mutableListOf<ParsedConfigEntry>()
        val resourceStream = javaClass.getResourceAsStream("/third/swagger.advanced.config")
            ?: throw AssertionError("Resource not found: /third/swagger.advanced.config")
        val content = resourceStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

        parseConfigContent(content, entries)
        return entries
    }

    private fun parseConfigContent(content: String, entries: MutableList<ParsedConfigEntry>) {
        val lines = content.lines()
        var currentKey: String? = null
        var currentValue = StringBuilder()
        var inMultiline = false

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                if (!inMultiline) continue
            }

            if (inMultiline) {
                if (trimmedLine.startsWith("```")) {
                    inMultiline = false
                    currentKey?.let {
                        entries.add(ParsedConfigEntry(it, currentValue.toString()))
                    }
                    currentKey = null
                    currentValue = StringBuilder()
                } else {
                    if (currentValue.isNotEmpty()) {
                        currentValue.append("\n")
                    }
                    currentValue.append(line)
                }
            } else {
                val idx = line.indexOf('=')
                if (idx > 0) {
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    if (value.startsWith("```")) {
                        currentKey = key
                        inMultiline = true
                        currentValue = StringBuilder()
                    } else {
                        entries.add(ParsedConfigEntry(key, value))
                    }
                }
            }
        }
    }

    private fun getAllScriptContent(config: List<ParsedConfigEntry>): String {
        val templateVars = mutableMapOf<String, String>()
        config.forEach { templateVars[it.key] = it.value }

        return config.joinToString("\n") { entry ->
            var content = entry.value
            var replaced = true
            while (replaced) {
                replaced = false
                for ((key, value) in templateVars) {
                    val placeholder = "\${$key}"
                    if (content.contains(placeholder)) {
                        content = content.replace(placeholder, value)
                        replaced = true
                    }
                }
            }
            content
        }
    }

    @Test
    fun testSwaggerAdvancedConfigFileExists() {
        val config = loadSwaggerAdvancedConfig()
        assertNotNull("Swagger advanced config should be loadable", config)
        assertTrue("Swagger advanced config should not be empty", config.isNotEmpty())
    }

    @Test
    fun testResolveApiImplicitParamScriptExists() {
        val config = loadSwaggerAdvancedConfig()
        val resolveScript = config.find { it.key == "resolve_api_implicit_param" }
        assertNotNull("resolve_api_implicit_param script should exist", resolveScript)
        assertTrue("Script should contain api references",
            resolveScript!!.value.contains("api."))
    }

    @Test
    fun testApiImplicitParamExportAfter() {
        val config = loadSwaggerAdvancedConfig()
        val entries = config.filter { it.key == "export.after[@io.swagger.annotations.ApiImplicitParam]" }
        assertTrue("Key should exist",
                entries.isNotEmpty())
        assertTrue("Should contain groovy",
                entries.any { it.value.contains("groovy:") || it.value.contains("groovy") })
    }

    @Test
    fun testApiImplicitParamsExportAfter() {
        val config = loadSwaggerAdvancedConfig()
        val entries = config.filter { it.key == "export.after[@io.swagger.annotations.ApiImplicitParams]" }
        assertTrue("Key should exist",
                entries.isNotEmpty())
        assertTrue("Should contain groovy",
                entries.any { it.value.contains("groovy:") || it.value.contains("groovy") })
    }

    @Test
    fun testResolveSwagger2ApiResponseScriptExists() {
        val config = loadSwaggerAdvancedConfig()
        val resolveScript = config.find { it.key == "resolve_swagger2_api_response" }
        assertNotNull("resolve_swagger2_api_response script should exist", resolveScript)
        assertTrue("Script should contain api references", resolveScript!!.value.contains("api."))
    }

    @Test
    fun testApiResponseExportAfter() {
        val config = loadSwaggerAdvancedConfig()
        val entries = config.filter { it.key == "export.after[@io.swagger.annotations.ApiResponse]" }
        assertTrue("Key should exist",
                entries.isNotEmpty())
        assertTrue("Should contain groovy",
                entries.any { it.value.contains("groovy:") || it.value.contains("groovy") })
    }

    @Test
    fun testApiResponsesExportAfter() {
        val config = loadSwaggerAdvancedConfig()
        val entries = config.filter { it.key == "export.after[@io.swagger.annotations.ApiResponses]" }
        assertTrue("Key should exist",
                entries.isNotEmpty())
        assertTrue("Should contain groovy",
                entries.any { it.value.contains("groovy:") || it.value.contains("groovy") })
    }

    @Test
    fun testDuplicateApiResponseExportAfter() {
        val config = loadSwaggerAdvancedConfig()
        val exportAfterKeys = config.filter { it.key == "export.after[@io.swagger.annotations.ApiResponse]" }
        assertTrue("Should have at least two ApiResponse entries",
                exportAfterKeys.size >= 2)
    }

    @Test
    fun testAllGroovyScriptsHaveApiCalls() {
        val config = loadSwaggerAdvancedConfig()
        val groovyScripts = config.filter { it.value.contains("groovy:") || it.value.contains("groovy") }

        assertTrue("Should have groovy scripts", groovyScripts.isNotEmpty())

        val allScriptContent = getAllScriptContent(config)
        assertTrue("At least some scripts should call api methods",
                allScriptContent.contains("api."))
    }

    @Test
    fun testConfigUsesAnnotationFilters() {
        val config = loadSwaggerAdvancedConfig()

        val swaggerAnnotations = listOf(
                "io.swagger.annotations.ApiImplicitParam",
                "io.swagger.annotations.ApiImplicitParams",
                "io.swagger.annotations.ApiResponse",
                "io.swagger.annotations.ApiResponses"
        )

        swaggerAnnotations.forEach { ann ->
            val key = "export.after[@$ann]"
            assertTrue("Config should have key for annotation: $ann",
                    config.any { it.key == key })
        }
    }

    @Test
    fun testConfigStructure() {
        val config = loadSwaggerAdvancedConfig()

        assertTrue("Should have template variables (keys without dots)",
                config.any { it.key == "resolve_api_implicit_param" })
        assertTrue("Should have template variables (keys without dots)",
                config.any { it.key == "resolve_swagger2_api_response" })
    }
}