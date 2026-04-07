package com.itangcent.easyapi.exporter.feign

import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class FeignAnnotationInheritanceTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var annotationHelper: UnifiedAnnotationHelper

    override fun setUp() {
        super.setUp()
        loadFile("spring/FeignClient.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RestController.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/feign/BaseUserApi.java")
        loadFile("api/feign/UserFeignClient.java")
        annotationHelper = UnifiedAnnotationHelper()
    }

    override fun createConfigReader() = TestConfigReader.EMPTY

    override fun customizeContext(builder: com.itangcent.easyapi.core.context.ActionContextBuilder) {
        builder.bind(DocHelper::class, StandardDocHelper())
    }

    fun testOverrideMethodInheritsGetMappingFromSuperInterface() = runTest {
        val psiClass = findClass("com.itangcent.api.feign.UserFeignClient")!!
        val getUserById = psiClass.findMethodsByName("getUserById", false).first()

        assertTrue(
            "Override without @GetMapping should inherit it from BaseUserApi",
            annotationHelper.hasAnn(getUserById, "org.springframework.web.bind.annotation.GetMapping")
        )
    }

    fun testDeclaredMethodsIncludeOverride() = runTest {
        val psiClass = findClass("com.itangcent.api.feign.UserFeignClient")!!
        val methodNames = psiClass.methods.map { it.name }

        assertTrue("getUserById override should be declared in UserFeignClient", "getUserById" in methodNames)
        assertTrue("getUserList should be declared in UserFeignClient", "getUserList" in methodNames)
    }
}
