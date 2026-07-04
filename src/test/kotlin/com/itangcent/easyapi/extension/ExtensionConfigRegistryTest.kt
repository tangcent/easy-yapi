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

    // ── Per-extension config tests ───────────────────────────────

    private fun assertExtensionLoaded(code: String): ExtensionConfig {
        val ext = ExtensionConfigRegistry.getExtension(code)
        assertNotNull("Extension '$code' should be loaded. Available: ${ExtensionConfigRegistry.codes().toList()}", ext)
        return ext!!
    }

    private fun assertContentContains(code: String, vararg fragments: String) {
        val ext = assertExtensionLoaded(code)
        for (fragment in fragments) {
            assertTrue("Extension '$code' should contain '$fragment'", ext.content.contains(fragment))
        }
    }

    // ── converts.config ──

    @Test
    fun testConvertsExtension() {
        val ext = assertExtensionLoaded("converts")
        assertEquals("Type conversions for common types (Date, ObjectId, etc.)", ext.description)
        assertNull("converts should have no on-class requirement", ext.onClass)
        assertTrue("converts should be default-enabled", ext.defaultEnabled)
        assertContentContains(
            "converts",
            "json.rule.convert[org.bson.types.ObjectId]=java.lang.String",
            "json.rule.convert[java.util.Date]=java.lang.String",
            "json.rule.convert[java.math.BigInteger]=java.lang.Long"
        )
    }

    // ── deprecated.config ──

    @Test
    fun testDeprecatedExtension() {
        val ext = assertExtensionLoaded("deprecated")
        assertEquals("Deprecated info for Java and Kotlin", ext.description)
        assertNull(ext.onClass)
        assertTrue(ext.defaultEnabled)
        assertContentContains(
            "deprecated",
            "method.doc[@java.lang.Deprecated]",
            "field.doc[@java.lang.Deprecated]",
            "method.doc[@kotlin.Deprecated]",
            "field.doc[@kotlin.Deprecated]"
        )
    }

    // ── fastjson.config ──

    @Test
    fun testFastjsonExtension() {
        val ext = assertExtensionLoaded("fastjson")
        assertEquals("Support for Fastjson annotations", ext.description)
        assertEquals("com.alibaba.fastjson.annotation.JSONField", ext.onClass)
        assertFalse("fastjson should be default-disabled", ext.defaultEnabled)
        assertContentContains(
            "fastjson",
            "field.name=@com.alibaba.fastjson.annotation.JSONField#value"
        )
    }

    // ── field-order-alphabetically.config ──

    @Test
    fun testFieldOrderAlphabeticallyExtension() {
        val ext = assertExtensionLoaded("field-order-alphabetically")
        assertEquals("Fields ordered alphabetically (A-Z)", ext.description)
        assertNull(ext.onClass)
        assertFalse(ext.defaultEnabled)
        assertContentContains(
            "field-order-alphabetically",
            "field.order.with=groovy:",
            "a.name().compareTo(b.name())"
        )
    }

    // ── field-order-alphabetically-desc.config ──

    @Test
    fun testFieldOrderAlphabeticallyDescExtension() {
        val ext = assertExtensionLoaded("field-order-alphabetically-desc")
        assertEquals("Fields ordered alphabetically descending (Z-A)", ext.description)
        assertNull(ext.onClass)
        assertFalse(ext.defaultEnabled)
        assertContentContains(
            "field-order-alphabetically-desc",
            "field.order.with=groovy:",
            "-a.name().compareTo(b.name())"
        )
    }

    // ── field-order-child-first.config ──

    @Test
    fun testFieldOrderChildFirstExtension() {
        val ext = assertExtensionLoaded("field-order-child-first")
        assertEquals("Child class fields first, parent class fields last", ext.description)
        assertNull(ext.onClass)
        assertFalse(ext.defaultEnabled)
        assertContentContains(
            "field-order-child-first",
            "field.order.with=groovy:",
            "aDefineClass.isExtend(bDefineClass.qualifiedName())"
        )
    }

    // ── field-order-parent-first.config ──

    @Test
    fun testFieldOrderParentFirstExtension() {
        val ext = assertExtensionLoaded("field-order-parent-first")
        assertEquals("Parent class fields first, child class fields last", ext.description)
        assertNull(ext.onClass)
        assertFalse(ext.defaultEnabled)
        assertContentContains(
            "field-order-parent-first",
            "field.order.with=groovy:",
            "aDefineClass.isExtend(bDefineClass.qualifiedName())"
        )
    }

    // ── field-utils.config ──

    @Test
    fun testFieldUtilsExtension() {
        val ext = assertExtensionLoaded("field-utils")
        assertEquals(
            "Field handling utilities (ignore system fields, transient, serialVersionUID, etc.)",
            ext.description
        )
        assertNull(ext.onClass)
        assertTrue(ext.defaultEnabled)
        assertContentContains(
            "field-utils",
            "field.ignore=groovy:it.hasModifier(\"transient\")",
            "constant.field.ignore=groovy:it.name()==\"serialVersionUID\"",
            "ignore_static_and_final_field=false"
        )
    }

    // ── gson.config ──

    @Test
    fun testGsonExtension() {
        val ext = assertExtensionLoaded("gson")
        assertEquals("Support for Gson annotations", ext.description)
        assertEquals("com.google.gson.annotations.SerializedName", ext.onClass)
        assertTrue(ext.defaultEnabled)
        assertContentContains(
            "gson",
            "field.name=@com.google.gson.annotations.SerializedName#value",
            "field.ignore=!@com.google.gson.annotations.Expose#serialize"
        )
    }

    // ── ignore.config ──

    @Test
    fun testIgnoreExtension() {
        val ext = assertExtensionLoaded("ignore")
        assertEquals("Ignore class/api", ext.description)
        assertNull(ext.onClass)
        assertTrue(ext.defaultEnabled)
        assertContentContains("ignore", "ignore=#ignore")
    }

    // ── jackson.config ──

    @Test
    fun testJacksonExtension() {
        val ext = assertExtensionLoaded("jackson")
        assertEquals("Support for Jackson annotations (basic + advanced)", ext.description)
        assertEquals("com.fasterxml.jackson.annotation.JsonProperty", ext.onClass)
        assertTrue(ext.defaultEnabled)
        assertContentContains(
            "jackson",
            "field.name=@com.fasterxml.jackson.annotation.JsonProperty#value",
            "field.ignore=@com.fasterxml.jackson.annotation.JsonIgnore#value",
            "JsonPropertyOrder",
            "JsonIgnoreProperties",
            "JsonUnwrapped",
            "JsonView"
        )
    }

    // ── jakarta-validation.config ──

    @Test
    fun testJakartaValidationExtension() {
        val ext = assertExtensionLoaded("jakarta-validation")
        assertEquals("Support for Jakarta validation annotations", ext.description)
        assertEquals("jakarta.validation.constraints.NotNull", ext.onClass)
        assertTrue(ext.defaultEnabled)
        assertContentContains(
            "jakarta-validation",
            "param.required=@jakarta.validation.constraints.NotBlank",
            "field.required=@jakarta.validation.constraints.NotNull",
            "param.required=@jakarta.validation.constraints.NotEmpty"
        )
    }

    // ── jakarta-validation-strict.config ──

    @Test
    fun testJakartaValidationStrictExtension() {
        val ext = assertExtensionLoaded("jakarta-validation-strict")
        assertEquals("Support for Jakarta validation annotations with strict group checking", ext.description)
        assertEquals("jakarta.validation.constraints.NotNull", ext.onClass)
        assertFalse(ext.defaultEnabled)
        assertContentContains(
            "jakarta-validation-strict",
            "check_annotated_Validated",
            "check_groups_jakarta",
            "session.set(\"json-group\""
        )
    }

    // ── javax-validation.config ──

    @Test
    fun testJavaxValidationExtension() {
        val ext = assertExtensionLoaded("javax-validation")
        assertEquals("Support for Javax validation annotations", ext.description)
        assertEquals("javax.validation.constraints.NotNull", ext.onClass)
        assertTrue(ext.defaultEnabled)
        assertContentContains(
            "javax-validation",
            "param.required=@javax.validation.constraints.NotBlank",
            "field.required=@javax.validation.constraints.NotNull",
            "param.required=@javax.validation.constraints.NotEmpty"
        )
    }

    // ── javax-validation-strict.config ──

    @Test
    fun testJavaxValidationStrictExtension() {
        val ext = assertExtensionLoaded("javax-validation-strict")
        assertEquals("Support for Javax validation annotations with strict group checking", ext.description)
        assertEquals("javax.validation.constraints.NotNull", ext.onClass)
        assertFalse(ext.defaultEnabled)
        assertContentContains(
            "javax-validation-strict",
            "check_annotated_Validated",
            "check_groups_javax",
            "session.set(\"json-group\""
        )
    }

    // ── spring.config ──

    @Test
    fun testSpringExtension() {
        val ext = assertExtensionLoaded("spring")
        assertEquals("Spring framework support (Entity, WebFlux, UI)", ext.description)
        assertEquals("org.springframework.http.HttpEntity", ext.onClass)
        assertTrue(ext.defaultEnabled)
        assertContentContains(
            "spring",
            "json.rule.convert[#regex:org.springframework.http.HttpEntity<(.*)>]=\${1}",
            "json.rule.convert[#regex:org.springframework.http.ResponseEntity<(.*)>]=\${1}",
            "json.rule.convert[org.springframework.http.HttpEntity]=java.lang.Object"
        )
    }

    // ── spring-configuration.config ──

    @Test
    fun testSpringConfigurationExtension() {
        val ext = assertExtensionLoaded("spring-configuration")
        assertEquals("Spring ConfigurationProperties support", ext.description)
        assertEquals("org.springframework.boot.context.properties.ConfigurationProperties", ext.onClass)
        assertTrue(ext.defaultEnabled)
        assertContentContains(
            "spring-configuration",
            "properties.prefix=@org.springframework.boot.context.properties.ConfigurationProperties",
            "properties.prefix=@org.springframework.boot.context.properties.ConfigurationProperties#prefix"
        )
    }

    // ── spring-properties.config ──

    @Test
    fun testSpringPropertiesExtension() {
        val ext = assertExtensionLoaded("spring-properties")
        assertEquals("Spring properties support (import and resolve application.properties/yml)", ext.description)
        assertNull(ext.onClass)
        assertFalse(ext.defaultEnabled)
        assertContentContains(
            "spring-properties",
            "properties.additional=\${module_path}/src/main/resources/application.properties",
            "properties.additional=\${module_path}/src/main/resources/application.yml",
            "class.prefix.path=\${server.servlet.context-path}"
        )
    }

    // ── spring-validations.config ──

    @Test
    fun testSpringValidationsExtension() {
        val ext = assertExtensionLoaded("spring-validations")
        assertEquals("Support for Spring validation annotations", ext.description)
        assertEquals("org.springframework.lang.NonNull", ext.onClass)
        assertTrue(ext.defaultEnabled)
        assertContentContains(
            "spring-validations",
            "field.required=@org.springframework.lang.NonNull",
            "param.ignore=groovy:it.type().isExtend(\"org.springframework.validation.BindingResult\")",
        )
    }

    // ── spring-webflux.config ──

    @Test
    fun testSpringWebFluxExtension() {
        val ext = assertExtensionLoaded("spring-webflux")
        assertEquals("Spring WebFlux reactive type support (Mono, Flux, Publisher)", ext.description)
        assertEquals("reactor.core.publisher.Mono", ext.onClass)
        assertTrue(ext.defaultEnabled)
        assertContentContains(
            "spring-webflux",
            "json.rule.convert[#regex:reactor.core.publisher.Mono<(.*)>]=\${1}",
            "json.rule.convert[#regex:reactor.core.publisher.Flux<(.*)>]=java.util.List<\${1}>",
            "json.rule.convert[org.reactivestreams.Publisher]=java.lang.Object"
        )
    }

    // ── swagger.config ──

    @Test
    fun testSwaggerExtension() {
        val ext = assertExtensionLoaded("swagger")
        assertEquals("Swagger 2.x annotation support", ext.description)
        assertEquals("io.swagger.annotations.Api", ext.onClass)
        assertTrue(ext.defaultEnabled)
        assertContentContains(
            "swagger",
            "param.doc=@io.swagger.annotations.ApiParam#value",
            "class.doc=@io.swagger.annotations.Api#tags",
            "method.doc=@io.swagger.annotations.ApiOperation#value"
        )
    }

    // ── swagger3.config ──

    @Test
    fun testSwagger3Extension() {
        val ext = assertExtensionLoaded("swagger3")
        assertEquals("OpenAPI 3.x / Swagger 3 annotation support", ext.description)
        assertEquals("io.swagger.v3.oas.annotations.Operation", ext.onClass)
        assertTrue(ext.defaultEnabled)
        assertContentContains(
            "swagger3",
            "api.name=@io.swagger.v3.oas.annotations.Operation#summary",
            "method.doc=@io.swagger.v3.oas.annotations.Operation#description",
            "field.name=@io.swagger.v3.oas.annotations.media.Schema#name",
            "export.after[@io.swagger.v3.oas.annotations.Parameter]"
        )
    }

    // ── All extensions count ──

    @Test
    fun testAllExtensionConfigsLoaded() {
        val expectedCodes = listOf(
            "converts", "deprecated", "fastjson",
            "field-order-alphabetically", "field-order-alphabetically-desc",
            "field-order-child-first", "field-order-parent-first",
            "field-utils", "gson", "ignore", "jackson",
            "jakarta-validation", "jakarta-validation-strict",
            "javax-validation", "javax-validation-strict",
            "mybatis-plus",
            "spring", "spring-configuration", "spring-properties",
            "spring-validations", "spring-webflux",
            "swagger", "swagger3",
            "yapi", "yapi-mock", "yapi-swagger", "yapi.project"
        )
        val actualCodes = ExtensionConfigRegistry.codes().toList()
        for (code in expectedCodes) {
            assertTrue("Extension '$code' should be loaded. Actual: $actualCodes", actualCodes.contains(code))
        }
        assertEquals(
            "Should have exactly ${expectedCodes.size} extensions",
            expectedCodes.size, actualCodes.size
        )
    }
}
