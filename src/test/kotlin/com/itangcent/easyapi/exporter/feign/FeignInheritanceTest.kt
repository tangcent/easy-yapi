package com.itangcent.easyapi.exporter.feign

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.model.path
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.UnifiedDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Tests that FeignClassExporter correctly exports methods inherited from a super interface,
 * including class-level @RequestMapping path prefixes.
 */
class FeignInheritanceTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: FeignClassExporter

    override fun setUp() {
        super.setUp()
        loadFile("spring/FeignClient.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/DeleteMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RestController.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/feign/BaseUserApi.java")
        loadFile("api/feign/UserFeignClient.java")
        exporter = FeignClassExporter(project, feignEnable = true)
    }

    override fun createConfigReader() = TestConfigReader.empty(project)


    fun testInheritedMethodIsExported() = runTest {
        val psiClass = findClass("com.itangcent.api.feign.UserFeignClient")!!
        val endpoints = exporter.export(psiClass)

        val getUserById = endpoints.find { it.path.contains("{userId}") && it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("getUserById inherited from BaseUserApi should be exported", getUserById)
    }

    fun testOwnMethodIsExported() = runTest {
        val psiClass = findClass("com.itangcent.api.feign.UserFeignClient")!!
        val endpoints = exporter.export(psiClass)

        val getUserList = endpoints.find { it.path.contains("list") && it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("getUserList declared in UserFeignClient should be exported", getUserList)
    }

    fun testInheritedMethodIncludesClassLevelPathPrefix() = runTest {
        val psiClass = findClass("com.itangcent.api.feign.UserFeignClient")!!
        val endpoints = exporter.export(psiClass)

        // BaseUserApi has @RequestMapping("/base-user"), getUserById has @GetMapping("/user/{userId}")
        // Full path should be /base-user/user/{userId}
        val getUserById = endpoints.find { it.path.contains("{userId}") && it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("getUserById should be exported", getUserById)
        assertTrue(
            "Path should include /base-user prefix from BaseUserApi, got: ${getUserById!!.path}",
            getUserById.path.contains("base-user")
        )
    }
}
