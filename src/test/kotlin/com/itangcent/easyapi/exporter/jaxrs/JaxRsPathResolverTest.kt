package com.itangcent.easyapi.exporter.jaxrs

import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class JaxRsPathResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var resolver: JaxRsPathResolver

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        resolver = JaxRsPathResolver(UnifiedAnnotationHelper())
    }

    private fun loadTestFiles() {
        loadFile("jaxrs/Path.java")
        loadFile("jaxrs/GET.java")
        loadFile("jaxrs/POST.java")
        loadFile("jaxrs/PUT.java")
        loadFile("jaxrs/DELETE.java")
        loadFile("jaxrs/PathParam.java")
        loadFile("jaxrs/QueryParam.java")
        loadFile("jaxrs/FormParam.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("constant/UserType.java")
        loadFile("api/jaxrs/UserResource.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testClassPath() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val path = resolver.classPath(psiClass!!)
        assertEquals("/user", path)
    }

    fun testMethodPath() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val method = findMethod(psiClass!!, "greeting")
        assertNotNull(method)

        val path = resolver.methodPath(method!!)
        assertEquals("/greeting", path)
    }

    fun testResolveCombinedPath() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val method = findMethod(psiClass!!, "greeting")
        assertNotNull(method)

        val path = resolver.resolve(psiClass, method!!)
        assertEquals("/user/greeting", path)
    }

    fun testResolvePathWithVariable() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val method = findMethod(psiClass!!, "get")
        assertNotNull(method)

        val path = resolver.resolve(psiClass, method!!)
        assertEquals("/user/get/{id}", path)
    }

    fun testResolveDeletePath() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val method = findMethod(psiClass!!, "delete")
        assertNotNull(method)

        val path = resolver.resolve(psiClass, method!!)
        assertEquals("/user/{id}", path)
    }

    fun testResolveListPath() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val method = findMethod(psiClass!!, "list")
        assertNotNull(method)

        val path = resolver.resolve(psiClass, method!!)
        assertEquals("/user/list", path)
    }
}
