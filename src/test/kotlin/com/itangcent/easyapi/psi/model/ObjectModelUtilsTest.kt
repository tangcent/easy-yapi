package com.itangcent.easyapi.psi.model

import junit.framework.TestCase

class ObjectModelUtilsTest : TestCase() {

    fun testAddCommentToTopLevelField() {
        val model = ObjectModelBuilder()
            .intField("code")
            .stringField("msg")
            .stringField("data")
            .build()

        val result = ObjectModelUtils.addFieldComment(model, "data", "output")

        assertNotNull(result)
        val obj = result!!.asObject()!!
        assertEquals("output", obj.fields["data"]!!.comment)
        // Other fields unchanged
        assertNull(obj.fields["code"]!!.comment)
        assertNull(obj.fields["msg"]!!.comment)
    }

    fun testAddCommentToNestedField() {
        val inner = ObjectModelBuilder()
            .stringField("name")
            .intField("age")
            .build()
        val model = ObjectModelBuilder()
            .intField("code")
            .objectField("data", inner)
            .build()

        val result = ObjectModelUtils.addFieldComment(model, "data.name", "用户名")

        assertNotNull(result)
        val dataObj = result!!.asObject()!!.fields["data"]!!.model.asObject()!!
        assertEquals("用户名", dataObj.fields["name"]!!.comment)
        assertNull(dataObj.fields["age"]!!.comment)
    }

    fun testAppendToExistingComment() {
        val model = ObjectModelBuilder()
            .field("data", ObjectModel.single("string"), comment = "原始描述")
            .build()

        val result = ObjectModelUtils.addFieldComment(model, "data", "追加描述")

        assertNotNull(result)
        val obj = result!!.asObject()!!
        assertEquals("原始描述\n追加描述", obj.fields["data"]!!.comment)
    }

    fun testFieldNotFound() {
        val model = ObjectModelBuilder()
            .intField("code")
            .build()

        val result = ObjectModelUtils.addFieldComment(model, "nonexistent", "comment")

        assertNull(result)
    }

    fun testNestedFieldNotFound() {
        val inner = ObjectModelBuilder()
            .stringField("name")
            .build()
        val model = ObjectModelBuilder()
            .objectField("data", inner)
            .build()

        val result = ObjectModelUtils.addFieldComment(model, "data.missing", "comment")

        assertNull(result)
    }

    fun testNonObjectModel() {
        val model = ObjectModel.single("string")

        val result = ObjectModelUtils.addFieldComment(model, "field", "comment")

        assertNull(result)
    }

    fun testArrayModel() {
        val model = ObjectModel.array(ObjectModel.single("string"))

        val result = ObjectModelUtils.addFieldComment(model, "field", "comment")

        assertNull(result)
    }

    fun testDeeplyNestedPath() {
        val level2 = ObjectModelBuilder()
            .stringField("value")
            .build()
        val level1 = ObjectModelBuilder()
            .objectField("inner", level2)
            .build()
        val root = ObjectModelBuilder()
            .objectField("outer", level1)
            .build()

        val result = ObjectModelUtils.addFieldComment(root, "outer.inner.value", "深层注释")

        assertNotNull(result)
        val value = result!!.asObject()!!
            .fields["outer"]!!.model.asObject()!!
            .fields["inner"]!!.model.asObject()!!
            .fields["value"]!!
        assertEquals("深层注释", value.comment)
    }

    fun testMiddlePathNotObject() {
        val model = ObjectModelBuilder()
            .stringField("data")
            .build()

        // "data" is a string, can't traverse into "data.name"
        val result = ObjectModelUtils.addFieldComment(model, "data.name", "comment")

        assertNull(result)
    }

    // ── findGenericFieldName tests ──

    fun testFindGenericFieldInResultLikeType() {
        // Simulates Result<T> with fields: code (int), msg (string), data (T → generic)
        val model = ObjectModel.Object(mapOf(
            "code" to FieldModel(ObjectModel.single("int")),
            "msg" to FieldModel(ObjectModel.single("string")),
            "data" to FieldModel(ObjectModel.single("object"), generic = true)
        ))

        assertEquals("data", ObjectModelUtils.findGenericFieldName(model))
    }

    fun testFindGenericFieldReturnsFirstMatch() {
        val model = ObjectModel.Object(linkedMapOf(
            "first" to FieldModel(ObjectModel.single("string"), generic = true),
            "second" to FieldModel(ObjectModel.single("int"), generic = true)
        ))

        assertEquals("first", ObjectModelUtils.findGenericFieldName(model))
    }

    fun testFindGenericFieldNoneGeneric() {
        val model = ObjectModelBuilder()
            .intField("code")
            .stringField("msg")
            .build()

        assertNull(ObjectModelUtils.findGenericFieldName(model))
    }

    fun testFindGenericFieldNonObject() {
        assertNull(ObjectModelUtils.findGenericFieldName(ObjectModel.single("string")))
        assertNull(ObjectModelUtils.findGenericFieldName(ObjectModel.array(ObjectModel.single("int"))))
    }
}
