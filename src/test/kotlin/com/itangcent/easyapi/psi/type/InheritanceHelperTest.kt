package com.itangcent.easyapi.psi.type

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class InheritanceHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        InheritanceHelper.clearCache()
    }

    fun testIsCollectionWithArrayList() {
        val psiClass = loadJavaCollectionClass("ArrayList")
        assertTrue("ArrayList should be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsCollectionWithHashSet() {
        val psiClass = loadJavaCollectionClass("HashSet")
        assertTrue("HashSet should be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsCollectionWithLinkedList() {
        val psiClass = loadJavaCollectionClass("LinkedList")
        assertTrue("LinkedList should be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsCollectionWithListInterface() {
        val psiClass = loadJavaCollectionClass("List")
        assertTrue("List should be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsCollectionWithSetInterface() {
        val psiClass = loadJavaCollectionClass("Set")
        assertTrue("Set should be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsCollectionWithCollectionInterface() {
        val psiClass = loadJavaCollectionClass("Collection")
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
        setupCollectionInheritanceStubs()
        myFixture.addClass("""
            package com.test;
            import java.util.ArrayList;
            public class MyList extends ArrayList<String> {}
        """.trimIndent())
        val psiClass = findClass("com.test.MyList")!!
        assertTrue("Subclass of ArrayList should be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsMapWithHashMap() {
        val psiClass = loadJavaMapClass("HashMap")
        assertTrue("HashMap should be detected as Map", InheritanceHelper.isMap(psiClass))
    }

    fun testIsMapWithLinkedHashMap() {
        val psiClass = loadJavaMapClass("LinkedHashMap")
        assertTrue("LinkedHashMap should be detected as Map", InheritanceHelper.isMap(psiClass))
    }

    fun testIsMapWithTreeMap() {
        val psiClass = loadJavaMapClass("TreeMap")
        assertTrue("TreeMap should be detected as Map", InheritanceHelper.isMap(psiClass))
    }

    fun testIsMapWithMapInterface() {
        val psiClass = loadJavaMapClass("Map")
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
        setupMapInheritanceStubs()
        myFixture.addClass("""
            package com.test;
            import java.util.HashMap;
            public class MyMap extends HashMap<String, Object> {}
        """.trimIndent())
        val psiClass = findClass("com.test.MyMap")!!
        assertTrue("Subclass of HashMap should be detected as Map", InheritanceHelper.isMap(psiClass))
    }

    fun testIsInheritorDirectMatch() {
        val psiClass = loadJavaMapClass("Map")
        assertTrue("Map should be inheritor of itself", InheritanceHelper.isInheritor(psiClass, "java.util.Map"))
    }

    fun testIsInheritorSubclass() {
        setupCollectionInheritanceStubs()
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
        val psiClass = loadJavaCollectionClass("ArrayList")
        val result1 = InheritanceHelper.isCollection(psiClass)
        val result2 = InheritanceHelper.isCollection(psiClass)
        assertEquals("Cached result should be the same", result1, result2)
    }

    fun testClearCache() {
        val psiClass = loadJavaCollectionClass("ArrayList")
        InheritanceHelper.isCollection(psiClass)
        InheritanceHelper.clearCache()
        val result = InheritanceHelper.isCollection(psiClass)
        assertTrue("Result should still be correct after cache clear", result)
    }

    fun testIsCollectionReturnsFalseForMap() {
        val psiClass = loadJavaMapClass("HashMap")
        assertFalse("HashMap should not be detected as Collection", InheritanceHelper.isCollection(psiClass))
    }

    fun testIsMapReturnsFalseForCollection() {
        val psiClass = loadJavaCollectionClass("ArrayList")
        assertFalse("ArrayList should not be detected as Map", InheritanceHelper.isMap(psiClass))
    }

    private fun setupCollectionInheritanceStubs() {
        myFixture.addClass("""
            package java.util;
            public interface Collection<E> extends Iterable<E> {}
        """.trimIndent())
        myFixture.addClass("""
            package java.util;
            public interface List<E> extends Collection<E> {}
        """.trimIndent())
        myFixture.addClass("""
            package java.util;
            public interface Set<E> extends Collection<E> {}
        """.trimIndent())
        myFixture.addClass("""
            package java.util;
            public abstract class AbstractCollection<E> implements Collection<E> {}
        """.trimIndent())
        myFixture.addClass("""
            package java.util;
            public abstract class AbstractList<E> extends AbstractCollection<E> implements List<E> {}
        """.trimIndent())
        myFixture.addClass("""
            package java.util;
            public class ArrayList<E> extends AbstractList<E> {}
        """.trimIndent())
    }

    private fun setupMapInheritanceStubs() {
        myFixture.addClass("""
            package java.util;
            public interface Map<K, V> {}
        """.trimIndent())
        myFixture.addClass("""
            package java.util;
            public abstract class AbstractMap<K, V> implements Map<K, V> {}
        """.trimIndent())
        myFixture.addClass("""
            package java.util;
            public class HashMap<K, V> extends AbstractMap<K, V> {}
        """.trimIndent())
    }

    private fun loadJavaCollectionClass(className: String): PsiClass {
        val fqn = "java.util.$className"
        val existing = findClass(fqn)
        if (existing != null) return existing
        myFixture.addClass("""
            package java.util;
            public class $className {}
        """.trimIndent())
        return findClass(fqn)!!
    }

    private fun loadJavaMapClass(className: String): PsiClass {
        val fqn = "java.util.$className"
        val existing = findClass(fqn)
        if (existing != null) return existing
        myFixture.addClass("""
            package java.util;
            public class $className {}
        """.trimIndent())
        return findClass(fqn)!!
    }
}
