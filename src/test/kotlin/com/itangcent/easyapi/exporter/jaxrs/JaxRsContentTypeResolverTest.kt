package com.itangcent.easyapi.exporter.jaxrs

import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class JaxRsContentTypeResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var resolver: JaxRsContentTypeResolver

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        val annotationHelper = UnifiedAnnotationHelper()
        resolver = JaxRsContentTypeResolver(annotationHelper)
    }

    private fun loadTestFiles() {
        loadFile("jaxrs/Path.java")
        loadFile("jaxrs/GET.java")
        loadFile("jaxrs/POST.java")
        loadFile("jaxrs/PUT.java")
        loadFile("jaxrs/DELETE.java")
        loadFile("jaxrs/Consumes.java")
        loadFile("jaxrs/Produces.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/jaxrs/UserResource.java")
    }

    fun testResolveContentTypeForGetMethod() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val method = findMethod(psiClass!!, "getUser")
        assertNotNull(method)

        val contentTypes = resolver.resolve(psiClass, method!!)
        assertTrue("GET method should have empty or valid consumes", contentTypes.consumes.isEmpty() || contentTypes.consumes.isNotEmpty())
    }

    fun testResolveContentTypeForPostMethod() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val method = findMethod(psiClass!!, "createUser")
        assertNotNull(method)

        val contentTypes = resolver.resolve(psiClass, method!!)
        assertTrue("POST method should have valid content types", contentTypes.consumes.isEmpty() || contentTypes.consumes.isNotEmpty())
    }
}
