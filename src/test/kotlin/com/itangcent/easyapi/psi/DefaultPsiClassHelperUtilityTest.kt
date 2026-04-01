package com.itangcent.easyapi.psi

import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelValueConverter
import com.itangcent.easyapi.psi.type.JsonType
import com.itangcent.easyapi.psi.type.PrimitiveKind
import junit.framework.TestCase

@OptIn(kotlin.ExperimentalStdlibApi::class)
class DefaultPsiClassHelperUtilityTest : TestCase() {

    private val helper = DefaultPsiClassHelper()

    fun testGetDefaultValueForPrimitiveBoolean() {
        val model = callGetDefaultValueForPrimitive(PrimitiveKind.BOOLEAN)
        assertEquals(false, ObjectModelValueConverter.toSimpleValue(model))
    }

    fun testGetDefaultValueForPrimitiveByte() {
        val model = callGetDefaultValueForPrimitive(PrimitiveKind.BYTE)
        assertEquals(0, ObjectModelValueConverter.toSimpleValue(model))
    }

    fun testGetDefaultValueForPrimitiveChar() {
        val model = callGetDefaultValueForPrimitive(PrimitiveKind.CHAR)
        assertEquals("", ObjectModelValueConverter.toSimpleValue(model))
    }

    fun testGetDefaultValueForPrimitiveShort() {
        val model = callGetDefaultValueForPrimitive(PrimitiveKind.SHORT)
        assertEquals(0, ObjectModelValueConverter.toSimpleValue(model))
    }

    fun testGetDefaultValueForPrimitiveInt() {
        val model = callGetDefaultValueForPrimitive(PrimitiveKind.INT)
        assertEquals(0, ObjectModelValueConverter.toSimpleValue(model))
    }

    fun testGetDefaultValueForPrimitiveLong() {
        val model = callGetDefaultValueForPrimitive(PrimitiveKind.LONG)
        assertEquals(0L, ObjectModelValueConverter.toSimpleValue(model))
    }

    fun testGetDefaultValueForPrimitiveFloat() {
        val model = callGetDefaultValueForPrimitive(PrimitiveKind.FLOAT)
        assertEquals(0.0f, ObjectModelValueConverter.toSimpleValue(model))
    }

    fun testGetDefaultValueForPrimitiveDouble() {
        val model = callGetDefaultValueForPrimitive(PrimitiveKind.DOUBLE)
        assertEquals(0.0, ObjectModelValueConverter.toSimpleValue(model))
    }

    fun testGetDefaultValueForPrimitiveVoid() {
        val model = callGetDefaultValueForPrimitive(PrimitiveKind.VOID)
        assertNull(ObjectModelValueConverter.toSimpleValue(model))
    }

    fun testGetDefaultValueForTypeBoolean() {
        val model = callGetDefaultValueForType("boolean")
        assertEquals(false, ObjectModelValueConverter.toSimpleValue(model))
        
        val wrapperModel = callGetDefaultValueForType("java.lang.Boolean")
        assertEquals(false, ObjectModelValueConverter.toSimpleValue(wrapperModel))
    }

    fun testGetDefaultValueForTypeByte() {
        val model = callGetDefaultValueForType("byte")
        assertEquals(0, ObjectModelValueConverter.toSimpleValue(model))
        
        val wrapperModel = callGetDefaultValueForType("java.lang.Byte")
        assertEquals(0, ObjectModelValueConverter.toSimpleValue(wrapperModel))
    }

    fun testGetDefaultValueForTypeChar() {
        val model = callGetDefaultValueForType("char")
        assertEquals("", ObjectModelValueConverter.toSimpleValue(model))
        
        val wrapperModel = callGetDefaultValueForType("java.lang.Character")
        assertEquals("", ObjectModelValueConverter.toSimpleValue(wrapperModel))
    }

    fun testGetDefaultValueForTypeShort() {
        val model = callGetDefaultValueForType("short")
        assertEquals(0, ObjectModelValueConverter.toSimpleValue(model))
        
        val wrapperModel = callGetDefaultValueForType("java.lang.Short")
        assertEquals(0, ObjectModelValueConverter.toSimpleValue(wrapperModel))
    }

    fun testGetDefaultValueForTypeInt() {
        val model = callGetDefaultValueForType("int")
        assertEquals(0, ObjectModelValueConverter.toSimpleValue(model))
        
        val wrapperModel = callGetDefaultValueForType("java.lang.Integer")
        assertEquals(0, ObjectModelValueConverter.toSimpleValue(wrapperModel))
    }

    fun testGetDefaultValueForTypeLong() {
        val model = callGetDefaultValueForType("long")
        assertEquals(0L, ObjectModelValueConverter.toSimpleValue(model))
        
        val wrapperModel = callGetDefaultValueForType("java.lang.Long")
        assertEquals(0L, ObjectModelValueConverter.toSimpleValue(wrapperModel))
    }

    fun testGetDefaultValueForTypeFloat() {
        val model = callGetDefaultValueForType("float")
        assertEquals(0.0f, ObjectModelValueConverter.toSimpleValue(model))
        
        val wrapperModel = callGetDefaultValueForType("java.lang.Float")
        assertEquals(0.0f, ObjectModelValueConverter.toSimpleValue(wrapperModel))
    }

    fun testGetDefaultValueForTypeDouble() {
        val model = callGetDefaultValueForType("double")
        assertEquals(0.0, ObjectModelValueConverter.toSimpleValue(model))
        
        val wrapperModel = callGetDefaultValueForType("java.lang.Double")
        assertEquals(0.0, ObjectModelValueConverter.toSimpleValue(wrapperModel))
    }

    fun testGetDefaultValueForTypeString() {
        val model = callGetDefaultValueForType("java.lang.String")
        assertEquals("", ObjectModelValueConverter.toSimpleValue(model))
    }

    fun testGetDefaultValueForTypeList() {
        val listModel = callGetDefaultValueForType("java.util.List")
        val value = ObjectModelValueConverter.toSimpleValue(listModel)
        assertTrue(value is List<*>)
        assertTrue((value as List<*>).isEmpty())

        val genericListModel = callGetDefaultValueForType("java.util.List<java.lang.String>")
        val genericValue = ObjectModelValueConverter.toSimpleValue(genericListModel)
        assertTrue(genericValue is List<*>)
        assertTrue((genericValue as List<*>).isEmpty())
    }

    fun testGetDefaultValueForTypeSet() {
        val setModel = callGetDefaultValueForType("java.util.Set")
        val value = ObjectModelValueConverter.toSimpleValue(setModel)
        assertTrue(value is List<*>)
        assertTrue((value as List<*>).isEmpty())

        val genericSetModel = callGetDefaultValueForType("java.util.Set<java.lang.Integer>")
        val genericValue = ObjectModelValueConverter.toSimpleValue(genericSetModel)
        assertTrue(genericValue is List<*>)
        assertTrue((genericValue as List<*>).isEmpty())
    }

    fun testGetDefaultValueForTypeMap() {
        val mapModel = callGetDefaultValueForType("java.util.Map")
        val value = ObjectModelValueConverter.toSimpleValue(mapModel)
        assertTrue(value is Map<*, *>)
        assertTrue((value as Map<*, *>).isEmpty())

        val genericMapModel = callGetDefaultValueForType("java.util.Map<java.lang.String, java.lang.Object>")
        val genericValue = ObjectModelValueConverter.toSimpleValue(genericMapModel)
        assertTrue(genericValue is Map<*, *>)
        assertTrue((genericValue as Map<*, *>).isEmpty())
    }

    fun testGetDefaultValueForUnknownType() {
        val model = callGetDefaultValueForType("com.example.UnknownType")
        val value = ObjectModelValueConverter.toSimpleValue(model)
        assertTrue(value is Map<*, *>)
        assertTrue((value as Map<*, *>).isEmpty())
        
        val objectModel = callGetDefaultValueForType("java.lang.Object")
        assertTrue(ObjectModelValueConverter.toSimpleValue(objectModel) is Map<*, *>)
    }

    fun testGetDefaultValueForComplexGenericList() {
        val model = callGetDefaultValueForType("java.util.List<java.util.List<java.lang.String>>")
        val value = ObjectModelValueConverter.toSimpleValue(model)
        assertTrue(value is List<*>)
        assertTrue((value as List<*>).isEmpty())
    }

    fun testGetDefaultValueForComplexGenericMap() {
        val model = callGetDefaultValueForType("java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>")
        val value = ObjectModelValueConverter.toSimpleValue(model)
        assertTrue(value is Map<*, *>)
        assertTrue((value as Map<*, *>).isEmpty())
    }

    fun testGetDefaultValueForWildcardList() {
        val model = callGetDefaultValueForType("java.util.List<?>")
        val value = ObjectModelValueConverter.toSimpleValue(model)
        assertTrue(value is List<*>)
        assertTrue((value as List<*>).isEmpty())
    }

    fun testGetDefaultValueForBoundedWildcardList() {
        val model = callGetDefaultValueForType("java.util.List<? extends java.lang.Number>")
        val value = ObjectModelValueConverter.toSimpleValue(model)
        assertTrue(value is List<*>)
        assertTrue((value as List<*>).isEmpty())
    }

    fun testGetDefaultValueForArrayList() {
        val model = callGetDefaultValueForType("java.util.ArrayList")
        val value = ObjectModelValueConverter.toSimpleValue(model)
        assertTrue(value is List<*>)
        assertTrue((value as List<*>).isEmpty())
    }

    fun testGetDefaultValueForHashMap() {
        val model = callGetDefaultValueForType("java.util.HashMap")
        val value = ObjectModelValueConverter.toSimpleValue(model)
        assertTrue(value is Map<*, *>)
        assertTrue((value as Map<*, *>).isEmpty())
    }

    fun testGetDefaultValueForLinkedList() {
        val model = callGetDefaultValueForType("java.util.LinkedList")
        val value = ObjectModelValueConverter.toSimpleValue(model)
        assertTrue(value is List<*>)
        assertTrue((value as List<*>).isEmpty())
    }

    fun testGetDefaultValueForTreeSet() {
        val model = callGetDefaultValueForType("java.util.TreeSet")
        val value = ObjectModelValueConverter.toSimpleValue(model)
        assertTrue(value is List<*>)
        assertTrue((value as List<*>).isEmpty())
    }

    fun testGetDefaultValueForConcurrentMap() {
        val model = callGetDefaultValueForType("java.util.concurrent.ConcurrentMap")
        val value = ObjectModelValueConverter.toSimpleValue(model)
        assertTrue(value is Map<*, *>)
        assertTrue((value as Map<*, *>).isEmpty())
    }

    fun testGetDefaultValueForNestedGenericTypes() {
        val model = callGetDefaultValueForType("java.util.Map<java.lang.String, java.util.Map<java.lang.Integer, java.util.List<java.lang.String>>>")
        val value = ObjectModelValueConverter.toSimpleValue(model)
        assertTrue(value is Map<*, *>)
        assertTrue((value as Map<*, *>).isEmpty())
    }

    fun testGetDefaultValueForSetWithGeneric() {
        val model = callGetDefaultValueForType("java.util.Set<java.lang.String>")
        val value = ObjectModelValueConverter.toSimpleValue(model)
        assertTrue(value is List<*>)
        assertTrue((value as List<*>).isEmpty())
    }

    fun testGetDefaultValueForPrimitiveWrapperTypes() {
        assertEquals(false, ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.Boolean")))
        assertEquals(0, ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.Byte")))
        assertEquals("", ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.Character")))
        assertEquals(0, ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.Short")))
        assertEquals(0, ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.Integer")))
        assertEquals(0L, ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.Long")))
        assertEquals(0.0f, ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.Float")))
        assertEquals(0.0, ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.Double")))
    }

    fun testGetDefaultValueForCommonJavaTypes() {
        assertTrue(ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.Object")) is Map<*, *>)
        
        val classValue = ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.Class"))
        assertTrue(classValue is Map<*, *>)
        assertTrue((classValue as Map<*, *>).isEmpty())
        
        val exceptionValue = ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.Exception"))
        assertTrue(exceptionValue is Map<*, *>)
        assertTrue((exceptionValue as Map<*, *>).isEmpty())
        
        val threadValue = ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.Thread"))
        assertTrue(threadValue is Map<*, *>)
        assertTrue((threadValue as Map<*, *>).isEmpty())
        
        val fileValue = ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.io.File"))
        assertTrue(fileValue is Map<*, *>)
        assertTrue((fileValue as Map<*, *>).isEmpty())
        
        assertEquals("", ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.util.Date")))
        assertEquals("", ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.time.LocalDate")))
        assertEquals("", ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.time.LocalDateTime")))
    }

    fun testPrimitiveAndWrapperConsistencyForBoolean() {
        val primitiveModel = callGetDefaultValueForPrimitive(PrimitiveKind.BOOLEAN)
        val wrapperModel = callGetDefaultValueForType("java.lang.Boolean")
        assertEquals(
            ObjectModelValueConverter.toSimpleValue(primitiveModel),
            ObjectModelValueConverter.toSimpleValue(wrapperModel)
        )
    }

    fun testPrimitiveAndWrapperConsistencyForByte() {
        val primitiveModel = callGetDefaultValueForPrimitive(PrimitiveKind.BYTE)
        val wrapperModel = callGetDefaultValueForType("java.lang.Byte")
        assertEquals(
            ObjectModelValueConverter.toSimpleValue(primitiveModel),
            ObjectModelValueConverter.toSimpleValue(wrapperModel)
        )
    }

    fun testPrimitiveAndWrapperConsistencyForChar() {
        val primitiveModel = callGetDefaultValueForPrimitive(PrimitiveKind.CHAR)
        val wrapperModel = callGetDefaultValueForType("java.lang.Character")
        assertEquals(
            ObjectModelValueConverter.toSimpleValue(primitiveModel),
            ObjectModelValueConverter.toSimpleValue(wrapperModel)
        )
    }

    fun testPrimitiveAndWrapperConsistencyForShort() {
        val primitiveModel = callGetDefaultValueForPrimitive(PrimitiveKind.SHORT)
        val wrapperModel = callGetDefaultValueForType("java.lang.Short")
        assertEquals(
            ObjectModelValueConverter.toSimpleValue(primitiveModel),
            ObjectModelValueConverter.toSimpleValue(wrapperModel)
        )
    }

    fun testPrimitiveAndWrapperConsistencyForInt() {
        val primitiveModel = callGetDefaultValueForPrimitive(PrimitiveKind.INT)
        val wrapperModel = callGetDefaultValueForType("java.lang.Integer")
        assertEquals(
            ObjectModelValueConverter.toSimpleValue(primitiveModel),
            ObjectModelValueConverter.toSimpleValue(wrapperModel)
        )
    }

    fun testPrimitiveAndWrapperConsistencyForLong() {
        val primitiveModel = callGetDefaultValueForPrimitive(PrimitiveKind.LONG)
        val wrapperModel = callGetDefaultValueForType("java.lang.Long")
        assertEquals(
            ObjectModelValueConverter.toSimpleValue(primitiveModel),
            ObjectModelValueConverter.toSimpleValue(wrapperModel)
        )
    }

    fun testPrimitiveAndWrapperConsistencyForFloat() {
        val primitiveModel = callGetDefaultValueForPrimitive(PrimitiveKind.FLOAT)
        val wrapperModel = callGetDefaultValueForType("java.lang.Float")
        assertEquals(
            ObjectModelValueConverter.toSimpleValue(primitiveModel),
            ObjectModelValueConverter.toSimpleValue(wrapperModel)
        )
    }

    fun testPrimitiveAndWrapperConsistencyForDouble() {
        val primitiveModel = callGetDefaultValueForPrimitive(PrimitiveKind.DOUBLE)
        val wrapperModel = callGetDefaultValueForType("java.lang.Double")
        assertEquals(
            ObjectModelValueConverter.toSimpleValue(primitiveModel),
            ObjectModelValueConverter.toSimpleValue(wrapperModel)
        )
    }

    fun testPrimitiveAndWrapperConsistencyForVoid() {
        val primitiveModel = callGetDefaultValueForPrimitive(PrimitiveKind.VOID)
        assertNull(ObjectModelValueConverter.toSimpleValue(primitiveModel))
    }

    fun testAllPrimitiveKindsHaveDefaults() {
        val allKinds = PrimitiveKind.entries
        for (kind in allKinds) {
            val model = callGetDefaultValueForPrimitive(kind)
            val default = ObjectModelValueConverter.toSimpleValue(model)
            if (kind == PrimitiveKind.VOID) {
                assertNull("VOID should have null default", default)
            } else {
                assertNotNull("${kind.name} should have a non-null default", default)
            }
        }
    }

    fun testPrimitiveDefaultsAreImmutable() {
        val model1 = callGetDefaultValueForPrimitive(PrimitiveKind.INT)
        val model2 = callGetDefaultValueForPrimitive(PrimitiveKind.INT)
        assertEquals(
            ObjectModelValueConverter.toSimpleValue(model1),
            ObjectModelValueConverter.toSimpleValue(model2)
        )
    }

    fun testGetDefaultValueForTypeWithSpaces() {
        val model = callGetDefaultValueForType("  java.lang.String  ")
        val value = ObjectModelValueConverter.toSimpleValue(model)
        assertTrue(value is Map<*, *>)
        assertTrue((value as Map<*, *>).isEmpty())
    }

    fun testGetDefaultValueForTypeWithNullInput() {
        val model = callGetDefaultValueForType("")
        assertEquals("", ObjectModelValueConverter.toSimpleValue(model))
    }

    fun testGetDefaultValueForTypeCaseSensitivity() {
        assertEquals("", ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.String")))
        assertEquals("", ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.string")))
        assertEquals("", ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("Java.lang.String")))
    }

    fun testGetDefaultValueForTypeWithArraySyntax() {
        val stringArrayValue = ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("java.lang.String[]"))
        assertTrue(stringArrayValue is List<*>)
        assertTrue((stringArrayValue as List<*>).isEmpty())
        
        val intArrayValue = ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("int[]"))
        assertTrue(intArrayValue is List<*>)
        assertTrue((intArrayValue as List<*>).isEmpty())
    }

    fun testGetDefaultValueForTypeWithPrimitiveArray() {
        val byteArrayValue = ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("byte[]"))
        assertTrue(byteArrayValue is List<*>)
        assertTrue((byteArrayValue as List<*>).isEmpty())
        
        val charArrayValue = ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("char[]"))
        assertTrue(charArrayValue is List<*>)
        assertTrue((charArrayValue as List<*>).isEmpty())
        
        val intArrayValue = ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("int[]"))
        assertTrue(intArrayValue is List<*>)
        assertTrue((intArrayValue as List<*>).isEmpty())
        
        val longArrayValue = ObjectModelValueConverter.toSimpleValue(callGetDefaultValueForType("long[]"))
        assertTrue(longArrayValue is List<*>)
        assertTrue((longArrayValue as List<*>).isEmpty())
    }

    private fun callGetDefaultValueForPrimitive(kind: PrimitiveKind): ObjectModel {
        val method = DefaultPsiClassHelper::class.java.getDeclaredMethod("getDefaultValueForPrimitive", PrimitiveKind::class.java)
        method.isAccessible = true
        return method.invoke(helper, kind) as ObjectModel
    }

    private fun callGetDefaultValueForType(typeName: String): ObjectModel? {
        return ObjectModel.single(JsonType.fromJavaType(typeName))
    }
}
