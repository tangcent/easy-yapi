package com.itangcent.easyapi.ide.action

import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.Json5Handler
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelJsonBuilder
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter
import com.itangcent.easyapi.psi.model.RawJsonHandler
import com.itangcent.easyapi.psi.type.JsonType
import org.junit.Assert.assertEquals
import org.junit.Test

class ObjectModelJsonConverterTest {

    @Test
    fun testToJson_null() {
        assertEquals("{}", ObjectModelJsonConverter.toJson(null))
    }

    @Test
    fun testToJson_singleTypes() {
        assertEquals("\"\"", ObjectModelJsonConverter.toJson(ObjectModel.Single(JsonType.STRING)))
        assertEquals("0", ObjectModelJsonConverter.toJson(ObjectModel.Single(JsonType.INT)))
        assertEquals("0", ObjectModelJsonConverter.toJson(ObjectModel.Single(JsonType.SHORT)))
        assertEquals("0", ObjectModelJsonConverter.toJson(ObjectModel.Single(JsonType.LONG)))
        assertEquals("0.0", ObjectModelJsonConverter.toJson(ObjectModel.Single(JsonType.FLOAT)))
        assertEquals("0.0", ObjectModelJsonConverter.toJson(ObjectModel.Single(JsonType.DOUBLE)))
        assertEquals("false", ObjectModelJsonConverter.toJson(ObjectModel.Single(JsonType.BOOLEAN)))
        assertEquals("null", ObjectModelJsonConverter.toJson(ObjectModel.Single("unknown")))
    }

    @Test
    fun testToJson_emptyObject() {
        val model = ObjectModel.Object(emptyMap())
        assertEquals("{}", ObjectModelJsonConverter.toJson(model))
    }

    @Test
    fun testToJson_simpleObject() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                "age" to FieldModel(ObjectModel.Single(JsonType.INT))
            )
        )
        val expected = "{\n" +
            "  \"name\": \"\",\n" +
            "  \"age\": 0\n" +
            "}"
        assertEquals(expected, ObjectModelJsonConverter.toJson(model))
    }

    @Test
    fun testToJson_nestedObject() {
        val model = ObjectModel.Object(
            mapOf(
                "user" to FieldModel(
                    ObjectModel.Object(
                        mapOf(
                            "name" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                            "age" to FieldModel(ObjectModel.Single(JsonType.INT))
                        )
                    )
                )
            )
        )
        val expected = "{\n" +
            "  \"user\": {\n" +
            "    \"name\": \"\",\n" +
            "    \"age\": 0\n" +
            "  }\n" +
            "}"
        assertEquals(expected, ObjectModelJsonConverter.toJson(model))
    }

    @Test
    fun testToJson_array() {
        val model = ObjectModel.Array(ObjectModel.Single(JsonType.STRING))
        val expected = "[\n" +
            "  \"\"\n" +
            "]"
        assertEquals(expected, ObjectModelJsonConverter.toJson(model))
    }

    @Test
    fun testToJson_arrayOfObject() {
        val model = ObjectModel.Array(
            ObjectModel.Object(
                mapOf(
                    "id" to FieldModel(ObjectModel.Single(JsonType.INT)),
                    "name" to FieldModel(ObjectModel.Single(JsonType.STRING))
                )
            )
        )
        val expected = "[\n" +
            "  {\n" +
            "    \"id\": 0,\n" +
            "    \"name\": \"\"\n" +
            "  }\n" +
            "]"
        assertEquals(expected, ObjectModelJsonConverter.toJson(model))
    }

    @Test
    fun testToJson_map() {
        val model = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            ObjectModel.Single(JsonType.INT)
        )
        val expected = "{\n" +
            "  \"\": 0\n" +
            "}"
        assertEquals(expected, ObjectModelJsonConverter.toJson(model))
    }

    @Test
    fun testToJson5_null() {
        assertEquals("{}", ObjectModelJsonConverter.toJson5(null))
    }

    @Test
    fun testToJson5_singleTypes() {
        assertEquals("\"\"", ObjectModelJsonConverter.toJson5(ObjectModel.Single(JsonType.STRING)))
        assertEquals("0", ObjectModelJsonConverter.toJson5(ObjectModel.Single(JsonType.INT)))
        assertEquals("false", ObjectModelJsonConverter.toJson5(ObjectModel.Single(JsonType.BOOLEAN)))
    }

    @Test
    fun testToJson5_withComments() {
        val model = ObjectModel.Object(
            mapOf(
                "id" to FieldModel(ObjectModel.Single(JsonType.INT), "Primary key"),
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "User name")
            )
        )
        val expected = "{\n" +
            "  \"id\": 0, // Primary key\n" +
            "  \"name\": \"\" // User name\n" +
            "}"
        assertEquals(expected, ObjectModelJsonConverter.toJson5(model))
    }

    @Test
    fun testToJson5_withBlockComment() {
        val model = ObjectModel.Object(
            mapOf(
                "user" to FieldModel(
                    ObjectModel.Object(
                        mapOf(
                            "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "User name")
                        )
                    ),
                    "User information"
                )
            )
        )
        val expected = "{\n" +
            "  /* User information */\n" +
            "  \"user\": {\n" +
            "    \"name\": \"\" // User name\n" +
            "  }\n" +
            "}"
        assertEquals(expected, ObjectModelJsonConverter.toJson5(model))
    }

    @Test
    fun testToJson5_keysAreQuoted() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                "user-name" to FieldModel(ObjectModel.Single(JsonType.STRING))
            )
        )
        val expected = "{\n" +
            "  \"name\": \"\",\n" +
            "  \"user-name\": \"\"\n" +
            "}"
        assertEquals(expected, ObjectModelJsonConverter.toJson5(model))
    }

    @Test
    fun testToJson_withCustomHandler() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING))
            )
        )
        val result = ObjectModelJsonConverter.toJson(model, Json5Handler)
        assert(result.contains("\"name\": \"\"")) { "Expected Json5 style output: $result" }
    }
}

class RawJsonHandlerTest {

    private val handler = RawJsonHandler
    private val builder = ObjectModelJsonBuilder(handler)

    @Test
    fun testNullModel() {
        assertEquals("{}", builder.build(null))
    }

    @Test
    fun testSingleValues() {
        assertEquals("\"\"", builder.build(ObjectModel.Single(JsonType.STRING)))
        assertEquals("0", builder.build(ObjectModel.Single(JsonType.INT)))
        assertEquals("0", builder.build(ObjectModel.Single(JsonType.SHORT)))
        assertEquals("0", builder.build(ObjectModel.Single(JsonType.LONG)))
        assertEquals("0.0", builder.build(ObjectModel.Single(JsonType.FLOAT)))
        assertEquals("0.0", builder.build(ObjectModel.Single(JsonType.DOUBLE)))
        assertEquals("false", builder.build(ObjectModel.Single(JsonType.BOOLEAN)))
        assertEquals("null", builder.build(ObjectModel.Single("unknown")))
    }

    @Test
    fun testEmptyObject() {
        assertEquals("{}", builder.build(ObjectModel.Object(emptyMap())))
    }

    @Test
    fun testSimpleObject() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "The name field"),
                "age" to FieldModel(ObjectModel.Single(JsonType.INT), "The age field")
            )
        )
        val expected = "{\n" +
            "  \"name\": \"\",\n" +
            "  \"age\": 0\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testNestedObject() {
        val model = ObjectModel.Object(
            mapOf(
                "user" to FieldModel(
                    ObjectModel.Object(
                        mapOf(
                            "name" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                            "age" to FieldModel(ObjectModel.Single(JsonType.INT))
                        )
                    )
                )
            )
        )
        val expected = "{\n" +
            "  \"user\": {\n" +
            "    \"name\": \"\",\n" +
            "    \"age\": 0\n" +
            "  }\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testArray() {
        val model = ObjectModel.Array(ObjectModel.Single(JsonType.STRING))
        val expected = "[\n" +
            "  \"\"\n" +
            "]"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testArrayOfObject() {
        val model = ObjectModel.Array(
            ObjectModel.Object(
                mapOf(
                    "id" to FieldModel(ObjectModel.Single(JsonType.INT)),
                    "name" to FieldModel(ObjectModel.Single(JsonType.STRING))
                )
            )
        )
        val expected = "[\n" +
            "  {\n" +
            "    \"id\": 0,\n" +
            "    \"name\": \"\"\n" +
            "  }\n" +
            "]"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testMap() {
        val model = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            ObjectModel.Single(JsonType.INT)
        )
        val expected = "{\n" +
            "  \"\": 0\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testComplexNestedStructure() {
        val model = ObjectModel.Object(
            mapOf(
                "data" to FieldModel(
                    ObjectModel.Object(
                        mapOf(
                            "items" to FieldModel(
                                ObjectModel.Array(
                                    ObjectModel.Object(
                                        mapOf(
                                            "id" to FieldModel(ObjectModel.Single(JsonType.INT)),
                                            "tags" to FieldModel(
                                                ObjectModel.Array(ObjectModel.Single(JsonType.STRING))
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
        val expected = "{\n" +
            "  \"data\": {\n" +
            "    \"items\": [\n" +
            "      {\n" +
            "        \"id\": 0,\n" +
            "        \"tags\": [\n" +
            "          \"\"\n" +
            "        ]\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testCommentsAreIgnored() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "This comment should not appear"),
                "age" to FieldModel(ObjectModel.Single(JsonType.INT), "Neither should this")
            )
        )
        val result = builder.build(model)
        assert(!result.contains("comment")) { "RawJsonHandler should not render comments: $result" }
        assert(!result.contains("//")) { "RawJsonHandler should not render comments: $result" }
        assert(!result.contains("/*")) { "RawJsonHandler should not render comments: $result" }
    }

    @Test
    fun testMapWithObjectValue() {
        val model = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            ObjectModel.Object(
                mapOf(
                    "id" to FieldModel(ObjectModel.Single(JsonType.INT)),
                    "name" to FieldModel(ObjectModel.Single(JsonType.STRING))
                )
            )
        )
        val expected = "{\n" +
            "  \"\": {\n" +
            "    \"id\": 0,\n" +
            "    \"name\": \"\"\n" +
            "  }\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testMapWithIntKey() {
        val model = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.INT),
            ObjectModel.Single(JsonType.STRING)
        )
        val expected = "{\n" +
            "  0: \"\"\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testMapWithIntKeyAndComplexValue() {
        val model = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.INT),
            ObjectModel.Object(
                mapOf(
                    "name" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                    "score" to FieldModel(ObjectModel.Single(JsonType.DOUBLE))
                )
            )
        )
        val expected = "{\n" +
            "  0: {\n" +
            "    \"name\": \"\",\n" +
            "    \"score\": 0.0\n" +
            "  }\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testArrayOfArray() {
        val model = ObjectModel.Array(
            ObjectModel.Array(ObjectModel.Single(JsonType.INT))
        )
        val expected = "[\n" +
            "  [\n" +
            "    0\n" +
            "  ]\n" +
            "]"
        assertEquals(expected, builder.build(model))
    }
}

class Json5HandlerTest {

    private val handler = Json5Handler
    private val builder = ObjectModelJsonBuilder(handler)

    @Test
    fun testNullModel() {
        assertEquals("{}", builder.build(null))
    }

    @Test
    fun testSingleValues() {
        assertEquals("\"\"", builder.build(ObjectModel.Single(JsonType.STRING)))
        assertEquals("0", builder.build(ObjectModel.Single(JsonType.INT)))
        assertEquals("0", builder.build(ObjectModel.Single(JsonType.SHORT)))
        assertEquals("0", builder.build(ObjectModel.Single(JsonType.LONG)))
        assertEquals("0.0", builder.build(ObjectModel.Single(JsonType.FLOAT)))
        assertEquals("0.0", builder.build(ObjectModel.Single(JsonType.DOUBLE)))
        assertEquals("false", builder.build(ObjectModel.Single(JsonType.BOOLEAN)))
        assertEquals("null", builder.build(ObjectModel.Single("unknown")))
    }

    @Test
    fun testEmptyObject() {
        assertEquals("{}", builder.build(ObjectModel.Object(emptyMap())))
    }

    @Test
    fun testEmptyFieldName() {
        val model = ObjectModel.Object(
            mapOf(
                "" to FieldModel(ObjectModel.Single(JsonType.STRING))
            )
        )
        val result = builder.build(model)
        assert(result.contains("\"\":")) { "Expected quoted empty field name: $result" }
    }

    @Test
    fun testSingleFieldWithEndlineComment() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "The name field")
            )
        )
        val expected = "{\n" +
            "  \"name\": \"\" // The name field\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testMultipleFieldsWithEndlineComments() {
        val model = ObjectModel.Object(
            mapOf(
                "id" to FieldModel(ObjectModel.Single(JsonType.INT), "The ID"),
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "The name")
            )
        )
        val expected = "{\n" +
            "  \"id\": 0, // The ID\n" +
            "  \"name\": \"\" // The name\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testSingleFieldWithMultilineComment() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "Line one\nLine two\nLine three")
            )
        )
        val expected = "{\n" +
            "  /*\n" +
            "   * Line one\n" +
            "   * Line two\n" +
            "   * Line three\n" +
            "   */\n" +
            "  \"name\": \"\"\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testObjectFieldWithBlockComment() {
        val model = ObjectModel.Object(
            mapOf(
                "user" to FieldModel(
                    ObjectModel.Object(
                        mapOf(
                            "name" to FieldModel(ObjectModel.Single(JsonType.STRING))
                        )
                    ),
                    "User information"
                )
            )
        )
        val expected = "{\n" +
            "  /* User information */\n" +
            "  \"user\": {\n" +
            "    \"name\": \"\"\n" +
            "  }\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testObjectFieldWithMultilineBlockComment() {
        val model = ObjectModel.Object(
            mapOf(
                "user" to FieldModel(
                    ObjectModel.Object(
                        mapOf(
                            "name" to FieldModel(ObjectModel.Single(JsonType.STRING))
                        )
                    ),
                    "User information\nContains name and age"
                )
            )
        )
        val expected = "{\n" +
            "  /*\n" +
            "   * User information\n" +
            "   * Contains name and age\n" +
            "   */\n" +
            "  \"user\": {\n" +
            "    \"name\": \"\"\n" +
            "  }\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testArrayOfSingleWithEndlineComment() {
        val model = ObjectModel.Object(
            mapOf(
                "tags" to FieldModel(
                    ObjectModel.Array(ObjectModel.Single(JsonType.STRING)),
                    "List of tags"
                )
            )
        )
        val expected = "{\n" +
            "  \"tags\": [\n" +
            "    \"\"\n" +
            "  ] // List of tags\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testArrayOfObjectWithBlockComment() {
        val model = ObjectModel.Object(
            mapOf(
                "users" to FieldModel(
                    ObjectModel.Array(
                        ObjectModel.Object(
                            mapOf(
                                "name" to FieldModel(ObjectModel.Single(JsonType.STRING))
                            )
                        )
                    ),
                    "List of users"
                )
            )
        )
        val expected = "{\n" +
            "  /* List of users */\n" +
            "  \"users\": [\n" +
            "    {\n" +
            "      \"name\": \"\"\n" +
            "    }\n" +
            "  ]\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testMapWithBlockComment() {
        val model = ObjectModel.Object(
            mapOf(
                "metadata" to FieldModel(
                    ObjectModel.MapModel(
                        ObjectModel.Single(JsonType.STRING),
                        ObjectModel.Single(JsonType.STRING)
                    ),
                    "Key-value metadata"
                )
            )
        )
        val expected = "{\n" +
            "  /* Key-value metadata */\n" +
            "  \"metadata\": {\n" +
            "    \"\": \"\"\n" +
            "  }\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testMapFieldWithoutComment() {
        val model = ObjectModel.Object(
            mapOf(
                "metadata" to FieldModel(
                    ObjectModel.MapModel(
                        ObjectModel.Single(JsonType.STRING),
                        ObjectModel.Single(JsonType.INT)
                    )
                )
            )
        )
        val expected = "{\n" +
            "  \"metadata\": {\n" +
            "    \"\": 0\n" +
            "  }\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testMixedFields() {
        val model = ObjectModel.Object(
            mapOf(
                "id" to FieldModel(ObjectModel.Single(JsonType.INT), "The ID"),
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "The name"),
                "address" to FieldModel(
                    ObjectModel.Object(
                        mapOf(
                            "city" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                            "country" to FieldModel(ObjectModel.Single(JsonType.STRING))
                        )
                    ),
                    "Address information"
                ),
                "tags" to FieldModel(
                    ObjectModel.Array(ObjectModel.Single(JsonType.STRING)),
                    "List of tags"
                )
            )
        )
        val expected = "{\n" +
            "  \"id\": 0, // The ID\n" +
            "  \"name\": \"\", // The name\n" +
            "  /* Address information */\n" +
            "  \"address\": {\n" +
            "    \"city\": \"\",\n" +
            "    \"country\": \"\"\n" +
            "  },\n" +
            "  \"tags\": [\n" +
            "    \"\"\n" +
            "  ] // List of tags\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testFieldWithoutComment() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING), null)
            )
        )
        val expected = "{\n" +
            "  \"name\": \"\"\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testFieldWithBlankComment() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "   ")
            )
        )
        val expected = "{\n" +
            "  \"name\": \"\"\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testAllKeysAreQuoted() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                "userName" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                "_private" to FieldModel(ObjectModel.Single(JsonType.INT)),
                "\$price" to FieldModel(ObjectModel.Single(JsonType.DOUBLE)),
                "user-name" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                "123field" to FieldModel(ObjectModel.Single(JsonType.INT)),
                "field name" to FieldModel(ObjectModel.Single(JsonType.STRING))
            )
        )
        val result = builder.build(model)
        assert(result.contains("\"name\":")) { "Expected '\"name\":' but got: $result" }
        assert(result.contains("\"userName\":")) { "Expected '\"userName\":' but got: $result" }
        assert(result.contains("\"_private\":")) { "Expected '\"_private\":' but got: $result" }
        assert(result.contains("\"\$price\":")) { "Expected '\"\$price\":' but got: $result" }
        assert(result.contains("\"user-name\":")) { "Expected '\"user-name\":' but got: $result" }
        assert(result.contains("\"123field\":")) { "Expected '\"123field\":' but got: $result" }
        assert(result.contains("\"field name\":")) { "Expected '\"field name\":' but got: $result" }
    }

    @Test
    fun testNestedObjectWithComments() {
        val model = ObjectModel.Object(
            mapOf(
                "user" to FieldModel(
                    ObjectModel.Object(
                        mapOf(
                            "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "User name"),
                            "profile" to FieldModel(
                                ObjectModel.Object(
                                    mapOf(
                                        "avatar" to FieldModel(ObjectModel.Single(JsonType.STRING), "Avatar URL")
                                    )
                                ),
                                "User profile"
                            )
                        )
                    ),
                    "User information"
                )
            )
        )
        val expected = "{\n" +
            "  /* User information */\n" +
            "  \"user\": {\n" +
            "    \"name\": \"\", // User name\n" +
            "    /* User profile */\n" +
            "    \"profile\": {\n" +
            "      \"avatar\": \"\" // Avatar URL\n" +
            "    }\n" +
            "  }\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testDeeplyNestedWithComments() {
        val model = ObjectModel.Object(
            mapOf(
                "level1" to FieldModel(
                    ObjectModel.Object(
                        mapOf(
                            "level2" to FieldModel(
                                ObjectModel.Object(
                                    mapOf(
                                        "value" to FieldModel(ObjectModel.Single(JsonType.STRING), "Deep value")
                                    )
                                ),
                                "Nested level 2"
                            )
                        )
                    ),
                    "Nested level 1"
                )
            )
        )
        val expected = "{\n" +
            "  /* Nested level 1 */\n" +
            "  \"level1\": {\n" +
            "    /* Nested level 2 */\n" +
            "    \"level2\": {\n" +
            "      \"value\": \"\" // Deep value\n" +
            "    }\n" +
            "  }\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testArrayOfArray() {
        val model = ObjectModel.Array(
            ObjectModel.Array(ObjectModel.Single(JsonType.INT))
        )
        val expected = "[\n" +
            "  [\n" +
            "    0\n" +
            "  ]\n" +
            "]"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testMapWithIntKeyAndObjectValue() {
        val model = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.INT),
            ObjectModel.Object(
                mapOf(
                    "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "User name"),
                    "age" to FieldModel(ObjectModel.Single(JsonType.INT), "User age")
                )
            )
        )
        val expected = "{\n" +
            "  0: {\n" +
            "    \"name\": \"\", // User name\n" +
            "    \"age\": 0 // User age\n" +
            "  }\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testMapWithComplexValueAndComment() {
        val model = ObjectModel.Object(
            mapOf(
                "users" to FieldModel(
                    ObjectModel.MapModel(
                        ObjectModel.Single(JsonType.LONG),
                        ObjectModel.Object(
                            mapOf(
                                "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "User name"),
                                "tags" to FieldModel(
                                    ObjectModel.Array(ObjectModel.Single(JsonType.STRING)),
                                    "User tags"
                                )
                            )
                        )
                    ),
                    "User map by ID"
                )
            )
        )
        val expected = "{\n" +
            "  /* User map by ID */\n" +
            "  \"users\": {\n" +
            "    0: {\n" +
            "      \"name\": \"\", // User name\n" +
            "      \"tags\": [\n" +
            "        \"\"\n" +
            "      ] // User tags\n" +
            "    }\n" +
            "  }\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }

    @Test
    fun testMapWithStringKeyAndSimpleValue() {
        val model = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            ObjectModel.Single(JsonType.BOOLEAN)
        )
        val expected = "{\n" +
            "  \"\": false\n" +
            "}"
        assertEquals(expected, builder.build(model))
    }
}
