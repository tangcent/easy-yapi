package com.itangcent.easyapi.psi.model

import com.itangcent.easyapi.psi.type.JsonType
import junit.framework.TestCase

class ObjectModelJsonBuilderTest : TestCase() {

    // ── Raw JSON basic tests ──

    fun testSimpleObject() {
        val model = ObjectModelBuilder()
            .stringField("name")
            .intField("age")
            .build()
        val json = ObjectModelJsonConverter.toJson(model)
        assertTrue(json.contains("\"name\": \"\""))
        assertTrue(json.contains("\"age\": 0"))
    }

    fun testEmptyObject() {
        val model = ObjectModel.emptyObject()
        val json = ObjectModelJsonConverter.toJson(model)
        assertEquals("{}", json.trim())
    }

    fun testNullModel() {
        val json = ObjectModelJsonConverter.toJson(null)
        assertEquals("{}", json)
    }

    fun testArrayModel() {
        val model = ObjectModel.array(ObjectModel.single(JsonType.STRING))
        val json = ObjectModelJsonConverter.toJson(model)
        assertTrue(json.contains("["))
        assertTrue(json.contains("\"\""))
        assertTrue(json.contains("]"))
    }

    fun testMapModel() {
        val model = ObjectModel.map(
            ObjectModel.single(JsonType.STRING),
            ObjectModel.single(JsonType.INT)
        )
        val json = ObjectModelJsonConverter.toJson(model)
        assertTrue(json.contains("{"))
        assertTrue(json.contains("}"))
    }

    // ── JSON5 comment tests ──

    fun testJson5SingleLineComment() {
        val model = ObjectModel.Object(linkedMapOf(
            "id" to FieldModel(ObjectModel.single(JsonType.LONG), comment = "user id"),
            "name" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user name")
        ))
        val json5 = ObjectModelJsonConverter.toJson5(model)
        assertTrue(json5.contains("// user id"))
        assertTrue(json5.contains("// user name"))
    }

    fun testJson5MultiLineCommentUsesBlockComment() {
        val model = ObjectModel.Object(linkedMapOf(
            "id" to FieldModel(
                ObjectModel.single(JsonType.LONG),
                comment = "Node ID\nUnique identifier"
            )
        ))
        val json5 = ObjectModelJsonConverter.toJson5(model)
        assertTrue("Should contain block comment", json5.contains("/*"))
        assertTrue("Should contain block comment end", json5.contains("*/"))
        assertFalse(
            "Should NOT concatenate lines in end-line comment",
            json5.contains("// Node IDUnique identifier")
        )
    }

    // ── Self-referencing / cycle detection (maxVisits=2) ──

    fun testSelfReferencingObjectExpandsTwoLevels() {
        // Node { id: Long, name: String, parent: Node }
        val fields = linkedMapOf<String, FieldModel>()
        val nodeModel = ObjectModel.Object(fields)

        fields["id"] = FieldModel(ObjectModel.single(JsonType.LONG))
        fields["name"] = FieldModel(ObjectModel.single(JsonType.STRING))
        fields["parent"] = FieldModel(nodeModel)

        val json = ObjectModelJsonConverter.toJson(nodeModel)

        // With maxVisits=2, the same object is expanded twice:
        // Level 1: { id, name, parent: { id, name, parent: {} } }
        val idCount = Regex("\"id\"").findAll(json).count()
        assertEquals("id should appear twice (root + one expansion)", 2, idCount)

        val nameCount = Regex("\"name\"").findAll(json).count()
        assertEquals("name should appear twice", 2, nameCount)

        // parent.parent should be empty
        val parentCount = Regex("\"parent\"").findAll(json).count()
        assertEquals("parent should appear twice (root.parent + root.parent.parent)", 2, parentCount)
    }

    fun testSelfReferencingObjectJson5() {
        val fields = linkedMapOf<String, FieldModel>()
        val nodeModel = ObjectModel.Object(fields)

        fields["id"] = FieldModel(ObjectModel.single(JsonType.LONG), comment = "Node ID")
        fields["name"] = FieldModel(ObjectModel.single(JsonType.STRING), comment = "Node Name")
        fields["parent"] = FieldModel(nodeModel, comment = "Parent Node")

        val json5 = ObjectModelJsonConverter.toJson5(nodeModel)

        // Should expand two levels with comments on both
        assertTrue("Should contain id", json5.contains("\"id\""))
        assertTrue("Should contain name", json5.contains("\"name\""))
        assertTrue("Should contain parent", json5.contains("\"parent\""))
        assertTrue("Should have id comment", json5.contains("// Node ID"))
        assertTrue("Should have name comment", json5.contains("// Node Name"))

        // id appears twice (root + parent expansion)
        val idCount = Regex("\"id\"").findAll(json5).count()
        assertEquals("id should appear twice in json5", 2, idCount)
    }

    fun testMutuallyReferencingObjects() {
        // A { name: String, b: B }, B { value: Int, a: A }
        val fieldsA = linkedMapOf<String, FieldModel>()
        val fieldsB = linkedMapOf<String, FieldModel>()
        val modelA = ObjectModel.Object(fieldsA)
        val modelB = ObjectModel.Object(fieldsB)

        fieldsA["name"] = FieldModel(ObjectModel.single(JsonType.STRING))
        fieldsA["b"] = FieldModel(modelB)

        fieldsB["value"] = FieldModel(ObjectModel.single(JsonType.INT))
        fieldsB["a"] = FieldModel(modelA)

        val json = ObjectModelJsonConverter.toJson(modelA)

        // A is visited once at root. B is visited once inside A.
        // B.a references A again (visit 2 for A) — expands A's fields.
        // Inside that A, B is visited again (visit 2 for B) — expands B's fields.
        // Then A inside that B would be visit 3 — cut off as {}.
        assertTrue(json.contains("\"name\""))
        assertTrue(json.contains("\"b\""))
        assertTrue(json.contains("\"value\""))

        // name appears in root A and in B.a (second visit of A)
        val nameCount = Regex("\"name\"").findAll(json).count()
        assertEquals("name should appear twice (A expanded twice)", 2, nameCount)
    }

    fun testArrayOfSelfReferencingObject() {
        // Node { id: Long, children: Node[] }
        val fields = linkedMapOf<String, FieldModel>()
        val nodeModel = ObjectModel.Object(fields)

        fields["id"] = FieldModel(ObjectModel.single(JsonType.LONG))
        fields["children"] = FieldModel(ObjectModel.array(nodeModel))

        val json = ObjectModelJsonConverter.toJson(nodeModel)

        assertTrue(json.contains("\"id\""))
        assertTrue(json.contains("\"children\""))
        assertTrue(json.contains("["))

        // id appears twice: root + array item (second visit)
        val idCount = Regex("\"id\"").findAll(json).count()
        assertEquals("id should appear twice (root + array item expansion)", 2, idCount)
    }

    fun testNonCyclicDuplicateTypeExpandsFully() {
        // Different instances — no cycle, both fully expanded
        val modelB1 = ObjectModelBuilder().stringField("x").build()
        val modelB2 = ObjectModelBuilder().stringField("x").build()

        val model = ObjectModel.Object(linkedMapOf(
            "first" to FieldModel(modelB1),
            "second" to FieldModel(modelB2)
        ))

        val json = ObjectModelJsonConverter.toJson(model)
        val xCount = Regex("\"x\"").findAll(json).count()
        assertEquals("x should appear twice (different instances)", 2, xCount)
    }

    fun testSameInstanceInSiblingFieldsExpandsBoth() {
        // Same instance used in two sibling fields — visit count resets after each
        val sharedModel = ObjectModelBuilder().stringField("x").build()

        val model = ObjectModel.Object(linkedMapOf(
            "first" to FieldModel(sharedModel),
            "second" to FieldModel(sharedModel)
        ))

        val json = ObjectModelJsonConverter.toJson(model)
        val xCount = Regex("\"x\"").findAll(json).count()
        assertEquals("x should appear twice (count restored after each visit)", 2, xCount)
    }

    fun testDeeplyNestedNonCyclicObject() {
        val inner = ObjectModelBuilder().stringField("leaf").build()
        val mid = ObjectModel.Object(linkedMapOf("inner" to FieldModel(inner)))
        val outer = ObjectModel.Object(linkedMapOf("mid" to FieldModel(mid)))

        val json = ObjectModelJsonConverter.toJson(outer)
        assertTrue(json.contains("\"mid\""))
        assertTrue(json.contains("\"inner\""))
        assertTrue(json.contains("\"leaf\""))
    }

    fun testCustomMaxVisitsOfOne() {
        // With maxVisits=1, self-reference should be {} immediately
        val fields = linkedMapOf<String, FieldModel>()
        val nodeModel = ObjectModel.Object(fields)

        fields["id"] = FieldModel(ObjectModel.single(JsonType.LONG))
        fields["parent"] = FieldModel(nodeModel)

        val builder = ObjectModelJsonBuilder(RawJsonHandler, maxVisits = 1)
        val json = builder.build(nodeModel)

        // id appears only once — parent is {} on first re-encounter
        val idCount = Regex("\"id\"").findAll(json).count()
        assertEquals("With maxVisits=1, id should appear once", 1, idCount)
    }

    fun testCustomMaxVisitsOfThree() {
        // With maxVisits=3, self-reference expands 3 levels
        val fields = linkedMapOf<String, FieldModel>()
        val nodeModel = ObjectModel.Object(fields)

        fields["id"] = FieldModel(ObjectModel.single(JsonType.LONG))
        fields["parent"] = FieldModel(nodeModel)

        val builder = ObjectModelJsonBuilder(RawJsonHandler, maxVisits = 3)
        val json = builder.build(nodeModel)

        val idCount = Regex("\"id\"").findAll(json).count()
        assertEquals("With maxVisits=3, id should appear three times", 3, idCount)
    }
}
