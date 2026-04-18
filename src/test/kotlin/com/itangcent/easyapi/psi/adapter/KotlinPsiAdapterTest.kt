package com.itangcent.easyapi.psi.adapter

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class KotlinPsiAdapterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var adapter: KotlinPsiAdapter

    override fun setUp() {
        super.setUp()
        adapter = KotlinPsiAdapter()
    }

    fun testDoesNotSupportJavaElement() {
        loadFile("adapter/JavaClassForKtTest.java", """
            package com.test.adapter;
            public class JavaClassForKtTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.JavaClassForKtTest")
        assertNotNull("Should find the Java class", psiClass)
        assertFalse("Adapter should not support Java element", adapter.supportsElement(psiClass!!))
    }

    fun testAdapterCanBeCreated() {
        assertNotNull("KotlinPsiAdapter should be created", adapter)
    }

    fun testImplementsPsiLanguageAdapter() {
        assertTrue("Should implement PsiLanguageAdapter", adapter is PsiLanguageAdapter)
    }

    fun testDelegatesToJavaPsiAdapter() {
        loadFile("adapter/KtDelegateTest.java", """
            package com.test.adapter;
            public class KtDelegateTest {
                public String name;
                public void doWork() {}
            }
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.KtDelegateTest")
        assertNotNull("Should find the class", psiClass)
        val methods = adapter.resolveMethods(psiClass!!)
        assertTrue("Should resolve methods via delegate", methods.isNotEmpty())
        val fields = adapter.resolveFields(psiClass)
        assertTrue("Should resolve fields via delegate", fields.isNotEmpty())
    }
}
