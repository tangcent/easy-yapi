package com.itangcent.easyapi.integration

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.exporter.feign.FeignClassExporter
import com.itangcent.easyapi.exporter.jaxrs.JaxRsClassExporter
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class MultiFrameworkExportTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var springExporter: SpringMvcClassExporter
    private lateinit var feignExporter: FeignClassExporter
    private lateinit var jaxrsExporter: JaxRsClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        springExporter = SpringMvcClassExporter(actionContext)
        feignExporter = FeignClassExporter(actionContext)
        jaxrsExporter = JaxRsClassExporter(actionContext)
    }

    private fun loadTestFiles() {
        loadFile("org/springframework/stereotype/Component.java")
        loadFile("org/springframework/stereotype/Controller.java")
        loadFile("spring/RestController.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/FeignClient.java")
        loadFile("jaxrs/Path.java")
        loadFile("jaxrs/GET.java")
        loadFile("jaxrs/POST.java")
        loadFile("jaxrs/PathParam.java")
        loadFile("jaxrs/QueryParam.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")
        loadFile("api/feign/UserClient.java")
        loadFile("api/jaxrs/UserResource.java")
    }

    override fun createConfigReader() = TestConfigReader.EMPTY

    override fun customizeContext(builder: com.itangcent.easyapi.core.context.ActionContextBuilder) {
        builder.bind(DocHelper::class, StandardDocHelper())
    }

    fun testExportSpringMvcController() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = springExporter.export(psiClass!!)
        assertTrue("Spring MVC exporter should export endpoints", endpoints.isNotEmpty())
    }

    fun testExportFeignClient() = runTest {
        val psiClass = findClass("com.itangcent.feign.UserClient")
        assertNotNull(psiClass)

        val endpoints = feignExporter.export(psiClass!!)
        assertTrue("Feign exporter should export endpoints", endpoints.isNotEmpty())
    }

    fun testExportJaxRsResource() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = jaxrsExporter.export(psiClass!!)
        assertTrue("JAX-RS exporter should export endpoints", endpoints.isNotEmpty())
    }
}
