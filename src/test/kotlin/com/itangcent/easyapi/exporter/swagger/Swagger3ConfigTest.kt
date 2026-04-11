package com.itangcent.easyapi.exporter.swagger

import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Swagger3ConfigTest {

    @Before
    fun setUp() {
        ExtensionConfigRegistry.loadExtensions()
    }

    private data class ParsedConfigEntry(val key: String, val value: String)

    private fun loadSwagger3Config(): List<ParsedConfigEntry> {
        val entries = mutableListOf<ParsedConfigEntry>()
        val swagger3 = ExtensionConfigRegistry.getExtension("swagger3")
            ?: throw AssertionError("Swagger3 extension not found")
        parseConfigContent(swagger3.content, entries)
        return entries
    }

    private fun parseConfigContent(content: String, entries: MutableList<ParsedConfigEntry>) {
        val lines = content.lines()
        var currentKey: String? = null
        var currentValue = StringBuilder()
        var inMultiline = false
        var inGroovyMultiline = false

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                if (!inMultiline && !inGroovyMultiline) continue
            }

            when {
                inMultiline -> {
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
                }
                inGroovyMultiline -> {
                    if (trimmedLine.startsWith("```")) {
                        inGroovyMultiline = false
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
                }
                else -> {
                    val idx = line.indexOf('=')
                    if (idx > 0) {
                        val key = line.substring(0, idx).trim()
                        val value = line.substring(idx + 1).trim()
                        when {
                            value.startsWith("```") -> {
                                currentKey = key
                                inMultiline = true
                                currentValue = StringBuilder()
                            }
                            value.startsWith("groovy:```") -> {
                                currentKey = key
                                inGroovyMultiline = true
                                currentValue = StringBuilder()
                                val scriptContent = value.removePrefix("groovy:```").removeSuffix("```")
                                if (scriptContent.isNotEmpty()) {
                                    currentValue.append(scriptContent)
                                }
                            }
                            else -> {
                                entries.add(ParsedConfigEntry(key, value))
                            }
                        }
                    }
                }
            }
        }

        if (inMultiline || inGroovyMultiline) {
            currentKey?.let {
                entries.add(ParsedConfigEntry(it, currentValue.toString()))
            }
        }
    }

    private fun Map<String, List<String>>.firstValue(key: String): String? = this[key]?.firstOrNull()

    private fun loadSwagger3ConfigAsMap(): Map<String, List<String>> {
        val config = mutableMapOf<String, MutableList<String>>()
        val swagger3 = ExtensionConfigRegistry.getExtension("swagger3")
            ?: throw AssertionError("Swagger3 extension not found")
        parseConfigContentToMap(swagger3.content, config)
        return config
    }

    private fun parseConfigContentToMap(content: String, config: MutableMap<String, MutableList<String>>) {
        val lines = content.lines()
        var currentKey: String? = null
        var currentValue = StringBuilder()
        var inMultiline = false
        var inGroovyMultiline = false

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                if (!inMultiline && !inGroovyMultiline) continue
            }

            when {
                inMultiline -> {
                    if (trimmedLine.startsWith("```")) {
                        inMultiline = false
                        currentKey?.let {
                            config.getOrPut(it) { mutableListOf() }.add(currentValue.toString())
                        }
                        currentKey = null
                        currentValue = StringBuilder()
                    } else {
                        if (currentValue.isNotEmpty()) {
                            currentValue.append("\n")
                        }
                        currentValue.append(line)
                    }
                }
                inGroovyMultiline -> {
                    if (trimmedLine.startsWith("```")) {
                        inGroovyMultiline = false
                        currentKey?.let {
                            config.getOrPut(it) { mutableListOf() }.add(currentValue.toString())
                        }
                        currentKey = null
                        currentValue = StringBuilder()
                    } else {
                        if (currentValue.isNotEmpty()) {
                            currentValue.append("\n")
                        }
                        currentValue.append(line)
                    }
                }
                else -> {
                    val idx = line.indexOf('=')
                    if (idx > 0) {
                        val key = line.substring(0, idx).trim()
                        val value = line.substring(idx + 1).trim()
                        when {
                            value.startsWith("```") -> {
                                currentKey = key
                                inMultiline = true
                                currentValue = StringBuilder()
                            }
                            value.startsWith("groovy:```") -> {
                                currentKey = key
                                inGroovyMultiline = true
                                currentValue = StringBuilder()
                                val scriptContent = value.removePrefix("groovy:```").removeSuffix("```")
                                if (scriptContent.isNotEmpty()) {
                                    currentValue.append(scriptContent)
                                }
                            }
                            else -> {
                                config.getOrPut(key) { mutableListOf() }.add(value)
                            }
                        }
                    }
                }
            }
        }

        if (inMultiline || inGroovyMultiline) {
            currentKey?.let {
                config.getOrPut(it) { mutableListOf() }.add(currentValue.toString())
            }
        }
    }

    @Test
    fun testSwagger3ConfigFileExists() {
        val config = loadSwagger3Config()
        assertNotNull("Swagger3 config should be loadable", config)
        assertTrue("Swagger3 config should not be empty", config.isNotEmpty())
    }

    @Test
    fun testSwagger3ConfigCompleteness() {
        val config = loadSwagger3ConfigAsMap()

        val expectedKeys = listOf(
                "ignore",
                "field.ignore",
                "param.ignore",
                "api.name",
                "method.doc",
                "method.default.http.method",
                "api.tag",
                "param.required",
                "param.doc",
                "field.required",
                "field.name",
                "field.doc"
        )

        expectedKeys.forEach { key ->
            assertTrue("Config should contain '$key'", config.containsKey(key))
        }
    }

    @Test
    fun testSwagger3HiddenAnnotationMappings() {
        val config = loadSwagger3ConfigAsMap()

        val ignoreValues = config["ignore"]
        assertNotNull("ignore should have values", ignoreValues)
        assertTrue("ignore should have Hidden annotation",
                ignoreValues?.any { it.contains("@io.swagger.v3.oas.annotations.Hidden") } == true)
    }

    @Test
    fun testSwagger3OperationMappings() {
        val config = loadSwagger3ConfigAsMap()

        val apiNameValues = config["api.name"]
        assertNotNull("api.name should have values", apiNameValues)
        assertTrue("api.name should have Operation#summary",
                apiNameValues?.any { it.contains("@io.swagger.v3.oas.annotations.Operation#summary") } == true)

        val methodDocValues = config["method.doc"]
        assertNotNull("method.doc should have values", methodDocValues)
        assertTrue("method.doc should have Operation#summary",
                methodDocValues?.any { it.contains("@io.swagger.v3.oas.annotations.Operation#summary") } == true)
        assertTrue("method.doc should have Operation#description",
                methodDocValues?.any { it.contains("@io.swagger.v3.oas.annotations.Operation#description") } == true)

        val methodHttpValues = config["method.default.http.method"]
        assertNotNull("method.default.http.method should have values", methodHttpValues)
        assertTrue("method.default.http.method should have Operation#method",
                methodHttpValues?.any { it.contains("@io.swagger.v3.oas.annotations.Operation#method") } == true)

        val apiTagValues = config["api.tag"]
        assertNotNull("api.tag should have values", apiTagValues)
        assertTrue("api.tag should have Operation#tags",
                apiTagValues?.any { it.contains("@io.swagger.v3.oas.annotations.Operation#tags") } == true)
    }

    @Test
    fun testSwagger3TagMappings() {
        val config = loadSwagger3ConfigAsMap()

        val tagsKey = "api.tag[@io.swagger.v3.oas.annotations.tags.Tags]"
        val tagsValues = config[tagsKey]
        assertNotNull("Should have Tags annotation mapping", tagsValues)
        assertTrue("Should use groovy collect",
                tagsValues?.any { it.contains("collect") } == true)
    }

    @Test
    fun testSwagger3SchemaMappings() {
        val config = loadSwagger3ConfigAsMap()

        val paramRequiredValues = config["param.required"]
        assertNotNull("param.required should have values", paramRequiredValues)
        assertTrue("param.required should use groovy for requiredMode",
                paramRequiredValues?.any { it.contains("groovy:") } == true)

        val fieldRequiredValues = config["field.required"]
        assertNotNull("field.required should have values", fieldRequiredValues)
        assertTrue("field.required should use groovy for requiredMode",
                fieldRequiredValues?.any { it.contains("groovy:") } == true)

        val paramDocValues = config["param.doc"]
        assertNotNull("param.doc should have values", paramDocValues)
        assertTrue("param.doc should have Schema#description",
                paramDocValues?.any { it.contains("@io.swagger.v3.oas.annotations.media.Schema#description") } == true)

        val paramIgnoreValues = config["param.ignore"]
        assertNotNull("param.ignore should have values", paramIgnoreValues)
        assertTrue("param.ignore should have Schema#hidden",
                paramIgnoreValues?.any { it.contains("@io.swagger.v3.oas.annotations.media.Schema#hidden") } == true)

        val fieldNameValues = config["field.name"]
        assertNotNull("field.name should have values", fieldNameValues)
        assertTrue("field.name should have Schema#name",
                fieldNameValues?.any { it.contains("@io.swagger.v3.oas.annotations.media.Schema#name") } == true)

        val fieldDocValues = config["field.doc"]
        assertNotNull("field.doc should have values", fieldDocValues)
        assertTrue("field.doc should have Schema#description",
                fieldDocValues?.any { it.contains("@io.swagger.v3.oas.annotations.media.Schema#description") } == true)

        val fieldIgnoreValues = config["field.ignore"]
        assertNotNull("field.ignore should have values", fieldIgnoreValues)
        assertTrue("field.ignore should have Schema#hidden",
                fieldIgnoreValues?.any { it.contains("@io.swagger.v3.oas.annotations.media.Schema#hidden") } == true)
    }

    @Test
    fun testSwagger3ParameterMappings() {
        val config = loadSwagger3ConfigAsMap()

        val paramIgnoreValues = config["param.ignore"]
        assertNotNull("param.ignore should have values", paramIgnoreValues)
        assertTrue("param.ignore should have Parameter#hidden",
                paramIgnoreValues?.any { it.contains("@io.swagger.v3.oas.annotations.Parameter#hidden") } == true)

        val paramRequiredValues = config["param.required"]
        assertNotNull("param.required should have values", paramRequiredValues)
        assertTrue("param.required should have Parameter#required",
                paramRequiredValues?.any { it.contains("@io.swagger.v3.oas.annotations.Parameter#required") } == true)

        val paramDocValues = config["param.doc"]
        assertNotNull("param.doc should have values", paramDocValues)
        assertTrue("param.doc should have Parameter#description",
                paramDocValues?.any { it.contains("@io.swagger.v3.oas.annotations.Parameter#description") } == true)
    }

    @Test
    fun testResolveParameterScriptExists() {
        val config = loadSwagger3Config()
        val resolveScript = config.find { it.key == "resolve_parameter" }
        assertNotNull("resolve_parameter script should exist", resolveScript)
        assertTrue("Script should contain map.description", resolveScript!!.value.contains("map.description"))
        assertTrue("Script should contain api.setParam", resolveScript.value.contains("api.setParam"))
    }

    @Test
    fun testParameterExportAfter() {
        val config = loadSwagger3Config()

        val exportAfter = config.filter { it.key == "export.after[@io.swagger.v3.oas.annotations.Parameter]" }
        assertTrue("Should have export.after for Parameter annotation",
                exportAfter.isNotEmpty())
        assertTrue("Should call resolve_parameter",
                exportAfter.any { it.value.contains("\${resolve_parameter}") })
    }

    @Test
    fun testParametersExportAfter() {
        val config = loadSwagger3Config()

        val exportAfter = config.filter { it.key == "export.after[@io.swagger.v3.oas.annotations.Parameters]" }
        assertTrue("Should have export.after for Parameters annotation",
                exportAfter.isNotEmpty())
        assertTrue("Should iterate over maps.value",
                exportAfter.any { it.value.contains("maps.value") })
    }

    @Test
    fun testOperationParametersExportAfter() {
        val config = loadSwagger3Config()

        val exportAfter = config.filter { it.key == "export.after[@io.swagger.v3.oas.annotations.Operation]" }
        assertTrue("Should have export.after for Operation annotation",
                exportAfter.isNotEmpty())
    }

    @Test
    fun testResolveSwagger3ApiResponseScriptExists() {
        val config = loadSwagger3Config()
        val resolveScript = config.find { it.key == "resolve_swagger3_api_response" }
        assertNotNull("resolve_swagger3_api_response script should exist", resolveScript)
        assertTrue("Script should set response code", resolveScript!!.value.contains("api.setResponseCode"))
        assertTrue("Script should append response body desc", resolveScript.value.contains("api.appendResponseBodyDesc"))
    }

    @Test
    fun testSwagger3ApiResponseExportAfter() {
        val config = loadSwagger3Config()

        val exportAfter = config.filter { it.key == "export.after[@io.swagger.v3.oas.annotations.responses.ApiResponse]" }
        assertTrue("Should have export.after for ApiResponse annotation",
                exportAfter.isNotEmpty())
        assertTrue("Should call resolve_swagger3_api_response",
                exportAfter.any { it.value.contains("\${resolve_swagger3_api_response}") })
    }

    @Test
    fun testSwagger3ApiResponsesExportAfter() {
        val config = loadSwagger3Config()

        val exportAfter = config.filter { it.key == "export.after[@io.swagger.v3.oas.annotations.responses.ApiResponses]" }
        assertTrue("Should have export.after for ApiResponses annotation",
                exportAfter.isNotEmpty())
        assertTrue("Should iterate over maps.value",
                exportAfter.any { it.value.contains("maps.value") })
    }

    @Test
    fun testAllSwagger3AnnotationsAreRecognized() {
        val config = loadSwagger3Config()
        val allValues = config.map { it.value }

        val swagger3Annotations = listOf(
                "io.swagger.v3.oas.annotations.Hidden",
                "io.swagger.v3.oas.annotations.Operation",
                "io.swagger.v3.oas.annotations.tags.Tag",
                "io.swagger.v3.oas.annotations.tags.Tags",
                "io.swagger.v3.oas.annotations.media.Schema",
                "io.swagger.v3.oas.annotations.Parameter",
                "io.swagger.v3.oas.annotations.Parameters",
                "io.swagger.v3.oas.annotations.responses.ApiResponse",
                "io.swagger.v3.oas.annotations.responses.ApiResponses"
        )

        swagger3Annotations.forEach { ann ->
            val found = allValues.any { it.startsWith("@$ann#") || it.contains(ann) }
            assertTrue("Annotation '$ann' should be mapped in config", found)
        }
    }

    @Test
    fun testConfigUsesTemplateVariables() {
        val config = loadSwagger3Config()

        assertTrue("Should have resolve_parameter template",
                config.any { it.key == "resolve_parameter" })
        assertTrue("Should have resolve_swagger3_api_response template",
                config.any { it.key == "resolve_swagger3_api_response" })
    }
}