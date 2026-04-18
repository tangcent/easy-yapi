package com.itangcent.easyapi.psi.adapter

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class JavaPsiAdapterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var adapter: JavaPsiAdapter

    override fun setUp() {
        super.setUp()
        adapter = JavaPsiAdapter()
    }

    fun testSupportsJavaElement() {
        loadFile("adapter/TestClass.java", """
            package com.test.adapter;
            public class TestClass {
                public void doSomething() {}
            }
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.TestClass")
        assertNotNull("Should find the Java class", psiClass)
        assertTrue("Adapter should support Java element", adapter.supportsElement(psiClass!!))
    }

    fun testResolveClassFromPsiClass() {
        loadFile("adapter/ResolveClassTest.java", """
            package com.test.adapter;
            public class ResolveClassTest {
                public String name;
                public void doWork() {}
            }
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.ResolveClassTest")
        assertNotNull("Should find the class", psiClass)
        val resolved = adapter.resolveClass(psiClass!!)
        assertNotNull("Should resolve class from PsiClass", resolved)
        assertEquals("com.test.adapter.ResolveClassTest", resolved!!.qualifiedName)
    }

    fun testResolveMethods() {
        loadFile("adapter/MethodTest.java", """
            package com.test.adapter;
            public class MethodTest {
                public void methodA() {}
                public void methodB() {}
            }
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.MethodTest")
        assertNotNull("Should find the class", psiClass)
        val methods = adapter.resolveMethods(psiClass!!)
        assertTrue("Should resolve at least 2 methods", methods.size >= 2)
        assertTrue("Should contain methodA", methods.any { it.name == "methodA" })
        assertTrue("Should contain methodB", methods.any { it.name == "methodB" })
    }

    fun testResolveFields() {
        loadFile("adapter/FieldTest.java", """
            package com.test.adapter;
            public class FieldTest {
                public String name;
                public int age;
            }
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.FieldTest")
        assertNotNull("Should find the class", psiClass)
        val fields = adapter.resolveFields(psiClass!!)
        assertTrue("Should resolve fields", fields.isNotEmpty())
    }

    fun testResolveAnnotations() {
        loadFile("adapter/AnnoTest.java", """
            package com.test.adapter;
            @Deprecated
            public class AnnoTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.AnnoTest")
        assertNotNull("Should find the class", psiClass)
        val annotations = adapter.resolveAnnotations(psiClass!!)
        assertTrue("Should resolve annotations", annotations.isNotEmpty())
        assertTrue("Should have @Deprecated annotation", annotations.any { it.qualifiedName?.contains("Deprecated") == true })
    }

    fun testResolveEnumConstants() {
        loadFile("adapter/EnumTest.java", """
            package com.test.adapter;
            public enum EnumTest {
                ALPHA, BETA, GAMMA
            }
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.EnumTest")
        assertNotNull("Should find the enum class", psiClass)
        val constants = adapter.resolveEnumConstants(psiClass!!)
        assertEquals("Should resolve 3 enum constants", 3, constants.size)
        assertTrue("Should contain ALPHA", constants.contains("ALPHA"))
        assertTrue("Should contain BETA", constants.contains("BETA"))
        assertTrue("Should contain GAMMA", constants.contains("GAMMA"))
    }

    fun testResolveEnumConstantsForNonEnum() {
        loadFile("adapter/NonEnumTest.java", """
            package com.test.adapter;
            public class NonEnumTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.NonEnumTest")
        assertNotNull("Should find the class", psiClass)
        val constants = adapter.resolveEnumConstants(psiClass!!)
        assertTrue("Non-enum class should have empty enum constants", constants.isEmpty())
    }

    fun testResolveDocComment() {
        loadFile("adapter/DocTest.java", """
            package com.test.adapter;
            /**
             * This is a test class.
             * @author TestAuthor
             */
            public class DocTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.DocTest")
        assertNotNull("Should find the class", psiClass)
        val docComment = adapter.resolveDocComment(psiClass!!)
        assertNotNull("Should resolve doc comment", docComment)
        assertTrue("Doc comment should contain class description", docComment!!.text.contains("This is a test class"))
    }
}
