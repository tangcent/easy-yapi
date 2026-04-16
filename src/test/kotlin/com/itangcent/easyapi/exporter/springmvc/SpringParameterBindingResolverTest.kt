package com.itangcent.easyapi.exporter.springmvc

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class SpringParameterBindingResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var resolver: SpringParameterBindingResolver

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        val annotationHelper = UnifiedAnnotationHelper()
        val engine = RuleEngine.getInstance(project)
        resolver = SpringParameterBindingResolver(annotationHelper, engine)
    }

    private fun loadTestFiles() {
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestHeader.java")
        loadFile("spring/ModelAttribute.java")
        loadFile("spring/CookieValue.java")
        loadFile("spring/SessionAttribute.java")
        loadFile("spring/RestController.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")
        loadFile("api/TestCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testResolvePathVariable() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")!!
        val method = findMethod(psiClass, "get")
        assertNotNull(method)

        val param = method!!.parameterList.parameters.find { it.name == "id" }
        assertNotNull(param)

        val binding = resolver.resolve(param!!)
        assertEquals(ParameterBinding.Path, binding)
    }

    fun testResolveRequestBody() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")!!
        val method = findMethod(psiClass, "create")
        assertNotNull(method)

        val param = method!!.parameterList.parameters.find { it.name == "userInfo" }
        assertNotNull(param)

        val binding = resolver.resolve(param!!)
        assertEquals(ParameterBinding.Body, binding)
    }

    fun testResolveModelAttribute() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")!!
        val method = findMethod(psiClass, "update")
        assertNotNull(method)

        val param = method!!.parameterList.parameters.find { it.name == "userInfo" }
        assertNotNull(param)

        val binding = resolver.resolve(param!!)
        assertEquals(ParameterBinding.Form, binding)
    }

    fun testResolveCookieValue() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")!!
        val method = findMethod(psiClass, "getByCookie")
        assertNotNull(method)

        val param = method!!.parameterList.parameters.find { it.name == "sessionId" }
        assertNotNull(param)

        val binding = resolver.resolve(param!!)
        assertEquals(ParameterBinding.Cookie, binding)
    }

    fun testResolveSessionAttribute() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")!!
        val method = findMethod(psiClass, "getFromSession")
        assertNotNull(method)

        val param = method!!.parameterList.parameters.find { it.name == "userId" }
        assertNotNull(param)

        val binding = resolver.resolve(param!!)
        assertEquals(ParameterBinding.Ignored, binding)
    }
}
