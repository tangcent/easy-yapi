package com.itangcent.easyapi.exporter.jaxrs

import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class JaxRsHttpMethodResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var resolver: JaxRsHttpMethodResolver

    override fun setUp() {
        super.setUp()
        val annotationHelper = UnifiedAnnotationHelper()
        resolver = JaxRsHttpMethodResolver(annotationHelper)
    }

    fun testResolveNoAnnotationReturnsNull() = runBlocking {
        loadFile("jaxrs/PlainMethodClass.java", """
            package com.test.jaxrs;
            public class PlainMethodClass {
                public void doSomething() {}
            }
        """.trimIndent())
        val psiClass = findClass("com.test.jaxrs.PlainMethodClass")!!
        val method = psiClass.findMethodsByName("doSomething", false).firstOrNull() ?: return@runBlocking
        val result = resolver.resolve(method)
        assertNull("Should return null for method without HTTP annotation", result)
    }
}
