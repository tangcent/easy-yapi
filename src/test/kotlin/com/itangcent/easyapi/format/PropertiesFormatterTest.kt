package com.itangcent.easyapi.format

import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.FieldOption
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import org.junit.Assert.*
import org.junit.Test

class PropertiesFormatterTest {

    @Test
    fun testFormatSingleValue() {
        val model = ObjectModel.Single(JsonType.STRING)
        val formatter = PropertiesFormatter()
        
        val result = formatter.format(model)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testFormatSingleValueWithDefault() {
        val model = ObjectModel.Single(JsonType.STRING)
        val fieldModel = FieldModel(
            model = model,
            defaultValue = "admin"
        )
        val obj = ObjectModel.Object(fields = mapOf("username" to fieldModel))
        val formatter = PropertiesFormatter()
        
        val result = formatter.format(obj)
        assertTrue(result.contains("username=admin"))
    }

    @Test
    fun testFormatObject() {
        val stringModel = ObjectModel.Single(JsonType.STRING)
        val field = FieldModel(
            model = stringModel,
            comment = "User name"
        )
        val obj = ObjectModel.Object(fields = mapOf("name" to field))
        val formatter = PropertiesFormatter()
        
        val result = formatter.format(obj)
        assertTrue(result.contains("# User name"))
        assertTrue(result.contains("name="))
    }

    @Test
    fun testFormatNestedObject() {
        val stringModel = ObjectModel.Single(JsonType.STRING)
        val nameField = FieldModel(model = stringModel)
        val userObj = ObjectModel.Object(fields = mapOf("name" to nameField))
        val userField = FieldModel(model = userObj)
        val rootObj = ObjectModel.Object(fields = mapOf("user" to userField))
        val formatter = PropertiesFormatter()
        
        val result = formatter.format(rootObj)
        assertTrue(result.contains("user.name="))
    }

    @Test
    fun testFormatArray() {
        val stringModel = ObjectModel.Single(JsonType.STRING)
        val arrayModel = ObjectModel.Array(stringModel)
        val field = FieldModel(model = arrayModel)
        val obj = ObjectModel.Object(fields = mapOf("tags" to field))
        val formatter = PropertiesFormatter()
        
        val result = formatter.format(obj)
        assertTrue(result.contains("tags=[]"))
    }

    @Test
    fun testFormatMap() {
        val stringModel = ObjectModel.Single(JsonType.STRING)
        val mapModel = ObjectModel.MapModel(ObjectModel.Single(JsonType.STRING), stringModel)
        val field = FieldModel(model = mapModel)
        val obj = ObjectModel.Object(fields = mapOf("metadata" to field))
        val formatter = PropertiesFormatter()
        
        val result = formatter.format(obj)
        assertTrue(result.contains("metadata={}"))
    }

    @Test
    fun testFormatWithOptions() {
        val stringModel = ObjectModel.Single(JsonType.STRING)
        val options = listOf(
            FieldOption(value = "active", desc = "Active user"),
            FieldOption(value = "inactive", desc = "Inactive user")
        )
        val field = FieldModel(
            model = stringModel,
            options = options
        )
        val obj = ObjectModel.Object(fields = mapOf("status" to field))
        val formatter = PropertiesFormatter()
        
        val result = formatter.format(obj)
        assertTrue(result.contains("status="))
        assertTrue(result.contains("active :Active user"))
        assertTrue(result.contains("inactive :Inactive user"))
    }

    @Test
    fun testFormatWithComment() {
        val stringModel = ObjectModel.Single(JsonType.STRING)
        val field = FieldModel(
            model = stringModel,
            comment = "User email address"
        )
        val obj = ObjectModel.Object(fields = mapOf("email" to field))
        val formatter = PropertiesFormatter()
        
        val result = formatter.format(obj)
        assertTrue(result.contains("# User email address"))
        assertTrue(result.contains("email="))
    }

    @Test
    fun testFormatRecursiveReference() {
        val obj = ObjectModel.Object(fields = mutableMapOf())
        val selfField = FieldModel(model = obj)
        (obj.fields as MutableMap)["parent"] = selfField
        val formatter = PropertiesFormatter(maxVisits = 2)
        
        val result = formatter.format(obj)
        assertTrue(result.contains("parent="))
    }

    @Test
    fun testFormatDifferentTypes() {
        val intModel = ObjectModel.Single(JsonType.INT)
        val boolModel = ObjectModel.Single(JsonType.BOOLEAN)
        val fields = mapOf(
            "count" to FieldModel(model = intModel),
            "enabled" to FieldModel(model = boolModel)
        )
        val obj = ObjectModel.Object(fields = fields)
        val formatter = PropertiesFormatter()
        
        val result = formatter.format(obj)
        assertTrue(result.contains("count=0"))
        assertTrue(result.contains("enabled=false"))
    }

    @Test
    fun testFormatWithPrefix() {
        val stringModel = ObjectModel.Single(JsonType.STRING)
        val nameField = FieldModel(model = stringModel)
        val obj = ObjectModel.Object(fields = mapOf("name" to nameField))
        val formatter = PropertiesFormatter()

        val result = formatter.format(obj, prefix = "my.app")
        assertTrue(result.contains("my.app.name="))
    }

    @Test
    fun testFormatWithPrefixNestedObject() {
        val stringModel = ObjectModel.Single(JsonType.STRING)
        val nameField = FieldModel(model = stringModel)
        val userObj = ObjectModel.Object(fields = mapOf("name" to nameField))
        val userField = FieldModel(model = userObj)
        val rootObj = ObjectModel.Object(fields = mapOf("user" to userField))
        val formatter = PropertiesFormatter()

        val result = formatter.format(rootObj, prefix = "spring")
        assertTrue(result.contains("spring.user.name="))
    }

    @Test
    fun testFormatWithEmptyPrefix() {
        val stringModel = ObjectModel.Single(JsonType.STRING)
        val nameField = FieldModel(model = stringModel)
        val obj = ObjectModel.Object(fields = mapOf("name" to nameField))
        val formatter = PropertiesFormatter()

        val resultWithoutPrefix = formatter.format(obj)
        val resultWithEmptyPrefix = formatter.format(obj, prefix = "")
        assertEquals(resultWithoutPrefix, resultWithEmptyPrefix)
    }
}
