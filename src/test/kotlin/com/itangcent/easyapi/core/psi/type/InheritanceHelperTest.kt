package com.itangcent.easyapi.core.psi.type

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class InheritanceHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        InheritanceHelper.clearCache()
    }

    fun testIsCollectionWithArrayList() {
        val psiClass = loadJDKClass("java.util.ArrayList")
        assertTrue("ArrayList should be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsCollectionWithHashSet() {
        val psiClass = loadJDKClass("java.util.HashSet")
        assertTrue("HashSet should be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsCollectionWithLinkedList() {
        val psiClass = loadJDKClass("java.util.LinkedList")
        assertTrue("LinkedList should be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsCollectionWithListInterface() {
        val psiClass = loadJDKClass("java.util.List")
        assertTrue("List should be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsCollectionWithSetInterface() {
        val psiClass = loadJDKClass("java.util.Set")
        assertTrue("Set should be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsCollectionWithCollectionInterface() {
        val psiClass = loadJDKClass("java.util.Collection")
        assertTrue("Collection should be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsCollectionWithNonCollectionClass() {
        val source = """
            package com.test;
            public class NotACollection {
                public int size() { return 0; }
            }
        """.trimIndent()
        myFixture.addClass(source)
        val psiClass = findClass("com.test.NotACollection")!!
        assertFalse("Non-collection class should not be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsCollectionWithCustomCollectionSubclass() {
        loadJDKClass("java.util.ArrayList")
        myFixture.addClass("""
            package com.test;
            import java.util.ArrayList;
            public class MyList extends ArrayList<String> {}
        """.trimIndent())
        val psiClass = findClass("com.test.MyList")!!
        assertTrue("Subclass of ArrayList should be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsMapWithHashMap() {
        val psiClass = loadJDKClass("java.util.HashMap")
        assertTrue("HashMap should be detected as Map", InheritanceHelper.isMap(psiClass))
    }

    fun testIsMapWithLinkedHashMap() {
        val psiClass = loadJDKClass("java.util.LinkedHashMap")
        assertTrue("LinkedHashMap should be detected as Map", InheritanceHelper.isMap(psiClass))
    }

    fun testIsMapWithTreeMap() {
        val psiClass = loadJDKClass("java.util.TreeMap")
        assertTrue("TreeMap should be detected as Map", InheritanceHelper.isMap(psiClass))
    }

    fun testIsMapWithMapInterface() {
        val psiClass = loadJDKClass("java.util.Map")
        assertTrue("Map interface should be detected as Map", InheritanceHelper.isMap(psiClass))
    }

    fun testIsMapWithNonMapClass() {
        val source = """
            package com.test;
            public class NotAMap {
                public Object get(Object key) { return null; }
            }
        """.trimIndent()
        myFixture.addClass(source)
        val psiClass = findClass("com.test.NotAMap")!!
        assertFalse("Non-map class should not be detected as Map", InheritanceHelper.isMap(psiClass))
    }

    fun testIsMapWithCustomMapSubclass() {
        loadJDKClass("java.util.HashMap")
        myFixture.addClass("""
            package com.test;
            import java.util.HashMap;
            public class MyMap extends HashMap<String, Object> {}
        """.trimIndent())
        val psiClass = findClass("com.test.MyMap")!!
        assertTrue("Subclass of HashMap should be detected as Map", InheritanceHelper.isMap(psiClass))
    }

    fun testIsInheritorDirectMatch() {
        val psiClass = loadJDKClass("java.util.Map")
        assertTrue("Map should be inheritor of itself", InheritanceHelper.isInheritor(psiClass, "java.util.Map"))
    }

    fun testIsInheritorSubclass() {
        loadJDKClass("java.util.ArrayList")
        myFixture.addClass("""
            package com.test;
            import java.util.ArrayList;
            public class MyList extends ArrayList<String> {}
        """.trimIndent())
        val psiClass = findClass("com.test.MyList")!!
        assertTrue("MyList should be inheritor of Collection", InheritanceHelper.isInheritor(psiClass, "java.util.Collection"))
    }

    fun testIsInheritorNotRelated() {
        val source = """
            package com.test;
            public class SimpleClass {}
        """.trimIndent()
        myFixture.addClass(source)
        val psiClass = findClass("com.test.SimpleClass")!!
        assertFalse("SimpleClass should not be inheritor of Collection", InheritanceHelper.isInheritor(psiClass, "java.util.Collection"))
    }

    fun testCacheReturnsSameResult() {
        val psiClass = loadJDKClass("java.util.ArrayList")
        val result1 = InheritanceHelper.isCollection(psiClass)
        val result2 = InheritanceHelper.isCollection(psiClass)
        assertEquals("Cached result should be the same", result1, result2)
    }

    fun testClearCache() {
        val psiClass = loadJDKClass("java.util.ArrayList")
        InheritanceHelper.isCollection(psiClass)
        InheritanceHelper.clearCache()
        val result = InheritanceHelper.isCollection(psiClass)
        assertTrue("Result should still be correct after cache clear", result)
    }

    fun testIsCollectionReturnsFalseForMap() {
        val psiClass = loadJDKClass("java.util.HashMap")
        assertFalse("HashMap should not be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsMapReturnsFalseForCollection() {
        val psiClass = loadJDKClass("java.util.ArrayList")
        assertFalse("ArrayList should not be detected as Map", InheritanceHelper.isMap(psiClass))
    }
}
