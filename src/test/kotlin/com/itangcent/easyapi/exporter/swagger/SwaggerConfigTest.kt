package com.itangcent.easyapi.exporter.swagger

import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SwaggerConfigTest {

    @Before
    fun setUp() {
        ExtensionConfigRegistry.loadExtensions()
    }

    private fun loadSwaggerConfig(): Map<String, List<String>> {
        val config = mutableMapOf<String, MutableList<String>>()
        val swagger = ExtensionConfigRegistry.getExtension("swagger")
            ?: throw AssertionError("Swagger extension not found")
        swagger.content.lines().forEach { line ->
            if (line.isNotBlank() && !line.startsWith("#") && line.contains("=")) {
                val idx = line.indexOf('=')
                if (idx > 0) {
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    config.getOrPut(key) { mutableListOf() }.add(value)
                }
            }
        }
        return config
    }

    @Test
    fun testSwaggerConfigFileExists() {
        val config = loadSwaggerConfig()
        assertNotNull("Swagger config should be loadable", config)
        assertTrue("Swagger config should not be empty", config.isNotEmpty())
    }

    @Test
    fun testSwaggerConfigCompleteness() {
        val config = loadSwaggerConfig()

        val expectedKeys = listOf(
                "param.doc",
                "param.default.value",
                "param.required",
                "param.ignore",
                "class.doc",
                "ignore",
                "json.rule.field.name",
                "field.ignore",
                "field.doc",
                "field.required",
                "method.doc",
                "api.tag"
        )

        expectedKeys.forEach { key ->
            assertTrue("Config should contain '$key'", config.containsKey(key))
        }
    }

    @Test
    fun testSwaggerConfigApiParamMappings() {
        val config = loadSwaggerConfig()

        assertTrue("param.doc should map to ApiParam#value",
                config["param.doc"]?.contains("@io.swagger.annotations.ApiParam#value") == true)
        assertTrue("param.default.value should map to ApiParam#defaultValue",
                config["param.default.value"]?.contains("@io.swagger.annotations.ApiParam#defaultValue") == true)
        assertTrue("param.required should map to ApiParam#required",
                config["param.required"]?.contains("@io.swagger.annotations.ApiParam#required") == true)
        assertTrue("param.ignore should map to ApiParam#hidden",
                config["param.ignore"]?.contains("@io.swagger.annotations.ApiParam#hidden") == true)
    }

    @Test
    fun testSwaggerConfigApiAnnotationsMappings() {
        val config = loadSwaggerConfig()

        assertTrue("class.doc should have Api#value",
                config["class.doc"]?.any { it.contains("@io.swagger.annotations.Api#value") } == true)
        assertTrue("class.doc should have Api#tags",
                config["class.doc"]?.any { it.contains("@io.swagger.annotations.Api#tags") } == true)
        assertTrue("ignore should map to Api#hidden",
                config["ignore"]?.contains("@io.swagger.annotations.Api#hidden") == true)
    }

    @Test
    fun testSwaggerConfigApiModelAnnotationsMappings() {
        val config = loadSwaggerConfig()

        assertTrue("class.doc should have ApiModel#value",
                config["class.doc"]?.any { it.contains("@io.swagger.annotations.ApiModel#value") } == true)
        assertTrue("class.doc should have ApiModel#description",
                config["class.doc"]?.any { it.contains("@io.swagger.annotations.ApiModel#description") } == true)
    }

    @Test
    fun testSwaggerConfigApiModelPropertyMappings() {
        val config = loadSwaggerConfig()

        assertTrue("json.rule.field.name should map to ApiModelProperty#name",
                config["json.rule.field.name"]?.contains("@io.swagger.annotations.ApiModelProperty#name") == true)
        assertTrue("field.ignore should map to ApiModelProperty#hidden",
                config["field.ignore"]?.contains("@io.swagger.annotations.ApiModelProperty#hidden") == true)
        assertTrue("field.doc should have ApiModelProperty#value",
                config["field.doc"]?.any { it.contains("@io.swagger.annotations.ApiModelProperty#value") } == true)
        assertTrue("field.doc should have ApiModelProperty#notes",
                config["field.doc"]?.any { it.contains("@io.swagger.annotations.ApiModelProperty#notes") } == true)
        assertTrue("field.required should map to ApiModelProperty#required",
                config["field.required"]?.contains("@io.swagger.annotations.ApiModelProperty#required") == true)
    }

    @Test
    fun testSwaggerConfigApiOperationMappings() {
        val config = loadSwaggerConfig()

        assertTrue("method.doc should map to ApiOperation#value",
                config["method.doc"]?.contains("@io.swagger.annotations.ApiOperation#value") == true)
        assertTrue("api.tag should map to ApiOperation#tags",
                config["api.tag"]?.contains("@io.swagger.annotations.ApiOperation#tags") == true)
    }



    @Test
    fun testAllSwagger2AnnotationsAreRecognized() {
        val config = loadSwaggerConfig()
        val allValues = config.values.flatten()

        val swagger2Annotations = listOf(
                "io.swagger.annotations.Api",
                "io.swagger.annotations.ApiModel",
                "io.swagger.annotations.ApiModelProperty",
                "io.swagger.annotations.ApiOperation",
                "io.swagger.annotations.ApiParam"
        )

        swagger2Annotations.forEach { ann ->
            val found = allValues.any { it.startsWith("@$ann#") }
            assertTrue("Annotation '$ann' should be mapped in config", found)
        }
    }

    @Test
    fun testDuplicateKeysHandled() {
        val config = loadSwaggerConfig()

        assertTrue("class.doc should have multiple values",
                (config["class.doc"]?.size ?: 0) > 1)
        assertTrue("field.doc should have multiple values",
                (config["field.doc"]?.size ?: 0) > 1)
    }
}