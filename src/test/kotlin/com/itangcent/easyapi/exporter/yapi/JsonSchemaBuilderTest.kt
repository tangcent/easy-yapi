package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.FieldOption
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import com.itangcent.easyapi.util.GsonUtils
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class JsonSchemaBuilderTest {

    private val builder = JsonSchemaBuilder()

    private fun parseJson(json: String): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return GsonUtils.fromJson<Map<String, Any?>>(json)
    }

    @Test
    fun testBuildNullModel() {
        val schema = parseJson(builder.build(null))
        assertEquals("http://json-schema.org/draft-04/schema#", schema["\$schema"])
        assertEquals("object", schema["type"])
    }

    @Test
    fun testBuildSimpleObject() {
        val model = ObjectModel.Object(
            mapOf(
                "code" to FieldModel(ObjectModel.Single(JsonType.INT), comment = "status code"),
                "msg" to FieldModel(ObjectModel.Single(JsonType.STRING), comment = "message"),
                "data" to FieldModel(ObjectModel.Object(emptyMap()), comment = "response data")
            )
        )
        val json = builder.build(model)
        val schema = parseJson(json)

        assertEquals("http://json-schema.org/draft-04/schema#", schema["\$schema"])
        assertEquals("object", schema["type"])

        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as Map<String, Any?>
        assertNotNull(properties)
        assertEquals(3, properties.size)

        @Suppress("UNCHECKED_CAST")
        val codeProp = properties["code"] as Map<String, Any?>
        assertEquals("integer", codeProp["type"])
        assertEquals("status code", codeProp["description"])

        @Suppress("UNCHECKED_CAST")
        val msgProp = properties["msg"] as Map<String, Any?>
        assertEquals("string", msgProp["type"])
        assertEquals("message", msgProp["description"])

        @Suppress("UNCHECKED_CAST")
        val dataProp = properties["data"] as Map<String, Any?>
        assertEquals("object", dataProp["type"])
        assertEquals("response data", dataProp["description"])
    }

    @Test
    fun testBuildEmptyObject() {
        val model = ObjectModel.Object(emptyMap())
        val schema = parseJson(builder.build(model))

        assertEquals("object", schema["type"])
        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as Map<String, Any?>
        assertTrue(properties.isEmpty())
    }

    @Test
    fun testBuildNestedObject() {
        val model = ObjectModel.Object(
            mapOf(
                "user" to FieldModel(
                    ObjectModel.Object(
                        mapOf(
                            "name" to FieldModel(ObjectModel.Single(JsonType.STRING), comment = "user name"),
                            "age" to FieldModel(ObjectModel.Single(JsonType.INT), comment = "user age")
                        )
                    ),
                    comment = "user info"
                ),
                "status" to FieldModel(ObjectModel.Single(JsonType.STRING))
            )
        )
        val schema = parseJson(builder.build(model))

        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as Map<String, Any?>
        assertTrue(properties.containsKey("user"))
        assertTrue(properties.containsKey("status"))

        @Suppress("UNCHECKED_CAST")
        val userProp = properties["user"] as Map<String, Any?>
        assertEquals("object", userProp["type"])
        assertEquals("user info", userProp["description"])

        @Suppress("UNCHECKED_CAST")
        val userProperties = userProp["properties"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val nameProp = userProperties["name"] as Map<String, Any?>
        assertEquals("string", nameProp["type"])
        assertEquals("user name", nameProp["description"])
    }

    @Test
    fun testBuildWithRequiredFields() {
        val model = ObjectModel.Object(
            mapOf(
                "id" to FieldModel(ObjectModel.Single(JsonType.LONG), required = true, comment = "primary key"),
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING), required = true),
                "nickname" to FieldModel(ObjectModel.Single(JsonType.STRING), required = false)
            )
        )
        val schema = parseJson(builder.build(model))

        @Suppress("UNCHECKED_CAST")
        val required = schema["required"] as List<String>
        assertEquals(2, required.size)
        assertTrue(required.contains("id"))
        assertTrue(required.contains("name"))
        assertFalse(required.contains("nickname"))
    }

    @Test
    fun testBuildWithDefaultValue() {
        val model = ObjectModel.Object(
            mapOf(
                "page" to FieldModel(ObjectModel.Single(JsonType.INT), defaultValue = "1"),
                "size" to FieldModel(ObjectModel.Single(JsonType.INT), defaultValue = "20")
            )
        )
        val schema = parseJson(builder.build(model))

        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val pageProp = properties["page"] as Map<String, Any?>
        assertEquals("1", pageProp["default"])

        @Suppress("UNCHECKED_CAST")
        val sizeProp = properties["size"] as Map<String, Any?>
        assertEquals("20", sizeProp["default"])
    }

    @Test
    fun testBuildWithMock() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING), mock = "@cname"),
                "id" to FieldModel(ObjectModel.Single(JsonType.INT), mock = "@integer(1,100)")
            )
        )
        val schema = parseJson(builder.build(model))

        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val nameProp = properties["name"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val nameMock = nameProp["mock"] as Map<String, Any?>
        assertEquals("@cname", nameMock["mock"])
    }

    @Test
    fun testBuildWithEnumOptions() {
        val model = ObjectModel.Object(
            mapOf(
                "status" to FieldModel(
                    ObjectModel.Single(JsonType.INT),
                    comment = "status code",
                    options = listOf(
                        FieldOption(0, "disabled"),
                        FieldOption(1, "enabled")
                    )
                )
            )
        )
        val schema = parseJson(builder.build(model))

        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val statusProp = properties["status"] as Map<String, Any?>
        assertEquals("integer", statusProp["type"])
        assertEquals("status code", statusProp["description"])

        @Suppress("UNCHECKED_CAST")
        val enumValues = statusProp["enum"] as List<Any?>
        assertEquals(2, enumValues.size)

        assertNotNull(statusProp["enumDesc"])
        assertNotNull(statusProp["mock"])
    }

    @Test
    fun testBuildArrayModel() {
        val model = ObjectModel.Array(
            ObjectModel.Object(
                mapOf(
                    "id" to FieldModel(ObjectModel.Single(JsonType.INT)),
                    "name" to FieldModel(ObjectModel.Single(JsonType.STRING))
                )
            )
        )
        val schema = parseJson(builder.build(model))

        assertEquals("array", schema["type"])
        @Suppress("UNCHECKED_CAST")
        val items = schema["items"] as Map<String, Any?>
        assertEquals("object", items["type"])
        @Suppress("UNCHECKED_CAST")
        val itemProps = items["properties"] as Map<String, Any?>
        assertTrue(itemProps.containsKey("id"))
        assertTrue(itemProps.containsKey("name"))
    }

    @Test
    fun testBuildMapModel() {
        val model = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            ObjectModel.Single(JsonType.INT)
        )
        val schema = parseJson(builder.build(model))

        assertEquals("object", schema["type"])
        @Suppress("UNCHECKED_CAST")
        val additionalProps = schema["additionalProperties"] as Map<String, Any?>
        assertEquals("integer", additionalProps["type"])
    }

    @Test
    fun testBuildWithRootDescription() {
        val model = ObjectModel.Object(
            mapOf("name" to FieldModel(ObjectModel.Single(JsonType.STRING)))
        )
        val schema = parseJson(builder.build(model, rootDesc = "User object"))

        assertEquals("User object", schema["description"])
        assertEquals("http://json-schema.org/draft-04/schema#", schema["\$schema"])
    }

    @Test
    fun testBuildOutputIsValidJson() {
        val model = ObjectModel.Object(
            mapOf(
                "code" to FieldModel(ObjectModel.Single(JsonType.INT), comment = "status code"),
                "msg" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                "data" to FieldModel(ObjectModel.Object(emptyMap()))
            )
        )
        val json = builder.build(model)

        // Should be valid JSON, not Kotlin map toString
        assertFalse("Output should not be Kotlin map format", json.contains("={"))
        assertTrue("Output should be valid JSON", json.startsWith("{"))

        // Parse to verify it's valid JSON
        val parsed = JsonParser.parseString(json)
        assertTrue(parsed.isJsonObject)
    }

    @Test
    fun testBuildWithAdvancedProperties() {
        val model = ObjectModel.Object(
            mapOf(
                "score" to FieldModel(
                    ObjectModel.Single(JsonType.DOUBLE),
                    advanced = mapOf("minimum" to 0, "maximum" to 100)
                )
            )
        )
        val schema = parseJson(builder.build(model))

        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val scoreProp = properties["score"] as Map<String, Any?>
        assertEquals("number", scoreProp["type"])
        assertNotNull(scoreProp["minimum"])
        assertNotNull(scoreProp["maximum"])
    }

    @Test
    fun testCircularReferenceProtection() {
        val innerFields = linkedMapOf<String, FieldModel>()
        val innerObj = ObjectModel.Object(innerFields)
        // Create a self-referencing structure
        val mutableFields = innerFields as MutableMap<String, FieldModel>
        mutableFields["self"] = FieldModel(innerObj)

        // Should not stack overflow
        val json = builder.build(innerObj)
        val schema = parseJson(json)
        assertEquals("object", schema["type"])
    }

    @Test
    fun testBuildAsMap() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING), comment = "user name")
            )
        )
        val schemaMap = builder.buildAsMap(model)

        // buildAsMap should return a Map, not include $schema header
        assertEquals("object", schemaMap["type"])
        assertNull(schemaMap["\$schema"])
        @Suppress("UNCHECKED_CAST")
        val properties = schemaMap["properties"] as Map<String, Any?>
        assertNotNull(properties["name"])
    }
}
