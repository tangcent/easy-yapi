package com.itangcent.easyapi.psi.type

import junit.framework.TestCase

class ClassNameConstantsTest : TestCase() {

    fun testMapTypesContainsCoreMapInterfaces() {
        assertTrue(ClassNameConstants.MAP_TYPES.contains(ClassNameConstants.JAVA_UTIL_MAP))
        assertTrue(ClassNameConstants.MAP_TYPES.contains(ClassNameConstants.JAVA_UTIL_HASHMAP))
        assertTrue(ClassNameConstants.MAP_TYPES.contains(ClassNameConstants.JAVA_UTIL_LINKEDHASHMAP))
        assertTrue(ClassNameConstants.MAP_TYPES.contains(ClassNameConstants.JAVA_UTIL_TREEMAP))
        assertTrue(ClassNameConstants.MAP_TYPES.contains(ClassNameConstants.JAVA_UTIL_CONCURRENTMAP))
        assertTrue(ClassNameConstants.MAP_TYPES.contains(ClassNameConstants.JAVA_UTIL_CONCURRENTHASHMAP))
    }

    fun testMapTypesDoesNotContainCollectionTypes() {
        assertFalse(ClassNameConstants.MAP_TYPES.contains(ClassNameConstants.JAVA_UTIL_COLLECTION))
        assertFalse(ClassNameConstants.MAP_TYPES.contains(ClassNameConstants.JAVA_UTIL_LIST))
        assertFalse(ClassNameConstants.MAP_TYPES.contains(ClassNameConstants.JAVA_UTIL_SET))
    }

    fun testCollectionTypesContainsCoreCollectionInterfaces() {
        assertTrue(ClassNameConstants.COLLECTION_TYPES.contains(ClassNameConstants.JAVA_UTIL_COLLECTION))
        assertTrue(ClassNameConstants.COLLECTION_TYPES.contains(ClassNameConstants.JAVA_UTIL_LIST))
        assertTrue(ClassNameConstants.COLLECTION_TYPES.contains(ClassNameConstants.JAVA_UTIL_SET))
        assertTrue(ClassNameConstants.COLLECTION_TYPES.contains(ClassNameConstants.JAVA_UTIL_ARRAYLIST))
        assertTrue(ClassNameConstants.COLLECTION_TYPES.contains(ClassNameConstants.JAVA_UTIL_HASHSET))
        assertTrue(ClassNameConstants.COLLECTION_TYPES.contains(ClassNameConstants.JAVA_UTIL_LINKEDLIST))
        assertTrue(ClassNameConstants.COLLECTION_TYPES.contains(ClassNameConstants.JAVA_UTIL_LINKEDHASHSET))
        assertTrue(ClassNameConstants.COLLECTION_TYPES.contains(ClassNameConstants.JAVA_UTIL_VECTOR))
        assertTrue(ClassNameConstants.COLLECTION_TYPES.contains(ClassNameConstants.JAVA_UTIL_STACK))
        assertTrue(ClassNameConstants.COLLECTION_TYPES.contains(ClassNameConstants.JAVA_UTIL_TREESET))
    }

    fun testCollectionTypesDoesNotContainMapTypes() {
        assertFalse(ClassNameConstants.COLLECTION_TYPES.contains(ClassNameConstants.JAVA_UTIL_MAP))
        assertFalse(ClassNameConstants.COLLECTION_TYPES.contains(ClassNameConstants.JAVA_UTIL_HASHMAP))
    }

    fun testConstantValuesAreFullyQualified() {
        assertEquals("java.util.Map", ClassNameConstants.JAVA_UTIL_MAP)
        assertEquals("java.util.HashMap", ClassNameConstants.JAVA_UTIL_HASHMAP)
        assertEquals("java.util.LinkedHashMap", ClassNameConstants.JAVA_UTIL_LINKEDHASHMAP)
        assertEquals("java.util.TreeMap", ClassNameConstants.JAVA_UTIL_TREEMAP)
        assertEquals("java.util.concurrent.ConcurrentMap", ClassNameConstants.JAVA_UTIL_CONCURRENTMAP)
        assertEquals("java.util.concurrent.ConcurrentHashMap", ClassNameConstants.JAVA_UTIL_CONCURRENTHASHMAP)

        assertEquals("java.util.Collection", ClassNameConstants.JAVA_UTIL_COLLECTION)
        assertEquals("java.util.List", ClassNameConstants.JAVA_UTIL_LIST)
        assertEquals("java.util.Set", ClassNameConstants.JAVA_UTIL_SET)
        assertEquals("java.util.ArrayList", ClassNameConstants.JAVA_UTIL_ARRAYLIST)
        assertEquals("java.util.HashSet", ClassNameConstants.JAVA_UTIL_HASHSET)
        assertEquals("java.util.LinkedList", ClassNameConstants.JAVA_UTIL_LINKEDLIST)
        assertEquals("java.util.LinkedHashSet", ClassNameConstants.JAVA_UTIL_LINKEDHASHSET)
        assertEquals("java.util.Vector", ClassNameConstants.JAVA_UTIL_VECTOR)
        assertEquals("java.util.Stack", ClassNameConstants.JAVA_UTIL_STACK)
        assertEquals("java.util.TreeSet", ClassNameConstants.JAVA_UTIL_TREESET)

        assertEquals("java.lang.Enum", ClassNameConstants.JAVA_LANG_ENUM)
        assertEquals("java.lang.Object", ClassNameConstants.JAVA_LANG_OBJECT)

        assertEquals("kotlin.collections.", ClassNameConstants.KOTLIN_COLLECTIONS_PREFIX)
    }

    fun testMapTypesIsNotEmpty() {
        assertFalse(ClassNameConstants.MAP_TYPES.isEmpty())
    }

    fun testCollectionTypesIsNotEmpty() {
        assertFalse(ClassNameConstants.COLLECTION_TYPES.isEmpty())
    }

    fun testNoOverlapBetweenMapAndCollectionTypes() {
        val overlap = ClassNameConstants.MAP_TYPES.intersect(ClassNameConstants.COLLECTION_TYPES)
        assertTrue("Map and Collection types should not overlap", overlap.isEmpty())
    }
}
