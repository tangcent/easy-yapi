package com.itangcent.easyapi.exporter.feign

import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class FeignPathResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var resolver: FeignPathResolver

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        resolver = FeignPathResolver(UnifiedAnnotationHelper())
    }

    private fun loadTestFiles() {
        loadFile("spring/FeignClient.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/DeleteMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestParam.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/feign/UserClient.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testResolveFeignClientWithName() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val info = resolver.resolve(psiClass!!)
        assertEquals("user", info.name)
    }

    fun testResolveNonFeignClient() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val info = resolver.resolve(psiClass!!)
        assertNull(info.path)
        assertNull(info.url)
        assertNull(info.name)
    }
}
