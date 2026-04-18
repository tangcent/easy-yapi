package com.itangcent.easyapi.psi.adapter

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ScalaPsiAdapterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var adapter: ScalaPsiAdapter

    override fun setUp() {
        super.setUp()
        adapter = ScalaPsiAdapter()
    }

    fun testDoesNotSupportJavaElement() {
        loadFile("adapter/ScalaJavaTest.java", """
            package com.test.adapter;
            public class ScalaJavaTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.ScalaJavaTest")
        assertNotNull("Should find the Java class", psiClass)
        assertFalse("Scala adapter should not support Java element", adapter.supportsElement(psiClass!!))
    }

    fun testAdapterCanBeCreated() {
        assertNotNull("ScalaPsiAdapter should be created", adapter)
    }

    fun testImplementsPsiLanguageAdapter() {
        assertTrue("Should implement PsiLanguageAdapter", adapter is PsiLanguageAdapter)
    }
}
