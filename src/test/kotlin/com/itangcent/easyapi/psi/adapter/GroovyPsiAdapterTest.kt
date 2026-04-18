package com.itangcent.easyapi.psi.adapter

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class GroovyPsiAdapterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var adapter: GroovyPsiAdapter

    override fun setUp() {
        super.setUp()
        adapter = GroovyPsiAdapter()
    }

    fun testDoesNotSupportJavaElement() {
        loadFile("adapter/GroovyJavaTest.java", """
            package com.test.adapter;
            public class GroovyJavaTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.GroovyJavaTest")
        assertNotNull("Should find the Java class", psiClass)
        assertFalse("Groovy adapter should not support Java element", adapter.supportsElement(psiClass!!))
    }

    fun testAdapterCanBeCreated() {
        assertNotNull("GroovyPsiAdapter should be created", adapter)
    }

    fun testImplementsPsiLanguageAdapter() {
        assertTrue("Should implement PsiLanguageAdapter", adapter is PsiLanguageAdapter)
    }
}
