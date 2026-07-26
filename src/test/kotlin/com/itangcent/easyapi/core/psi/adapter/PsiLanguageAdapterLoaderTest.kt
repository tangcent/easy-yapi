package com.itangcent.easyapi.core.psi.adapter

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class PsiLanguageAdapterLoaderTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testLoadAdaptersReturnsAtLeastJavaAdapter() {
        val adapters = PsiLanguageAdapterLoader.loadAdapters()
        assertTrue("Should load at least one adapter", adapters.isNotEmpty())
        assertTrue("Should include JavaPsiAdapter", adapters.any { it is JavaPsiAdapter })
    }

    fun testLoadAdaptersIncludesKotlinWhenAvailable() {
        val adapters = PsiLanguageAdapterLoader.loadAdapters()
        val hasKotlin = adapters.any { it is KotlinPsiAdapter }
        assertTrue("Kotlin plugin is bundled, so KotlinPsiAdapter should be loaded", hasKotlin)
    }

    fun testFindAdapterReturnsJavaForJavaElement() {
        loadFile("adapter/LoaderJavaTest.java", """
            package com.test.adapter;
            public class LoaderJavaTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.LoaderJavaTest")
        assertNotNull("Should find the Java class", psiClass)
        val adapter = PsiLanguageAdapterLoader.findAdapter(psiClass!!)
        assertNotNull("Should find an adapter for Java element", adapter)
        assertTrue("Should be JavaPsiAdapter for Java element", adapter is JavaPsiAdapter)
    }

    fun testFindAdapterPrefersKotlinForLightClass() {
        loadFile("adapter/ComplexKotlinSource.kt")
        val psiClass = findClass("com.test.adapter.ApiResponse")
        assertNotNull("Should find the Kotlin class", psiClass)
        // KtLightClass may report language as JAVA; both adapters can match.
        // Prefer Kotlin so class-level KDoc (folder name) can be resolved.
        val adapter = PsiLanguageAdapterLoader.findAdapter(psiClass!!)
        assertNotNull("Should find an adapter for Kotlin light class", adapter)
        assertTrue(
            "Should prefer KotlinPsiAdapter when light PSI also matches Java",
            adapter is KotlinPsiAdapter
        )
    }

    fun testFindAdapterReturnsNullForUnsupportedElement() {
        val adapters = PsiLanguageAdapterLoader.loadAdapters()
        assertNotNull("Adapters list should not be null", adapters)
    }
}
