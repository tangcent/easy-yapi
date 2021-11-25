package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.kit.toJson
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case of [DefaultSpringRequestMappingResolver]
 */
internal class DefaultSpringRequestMappingResolverTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var springRequestMappingResolver: SpringRequestMappingResolver

    private lateinit var myCtrlPsiClass: PsiClass

    private lateinit var userCtrlPsiClass: PsiClass

    override fun beforeBind() {
        super.beforeBind()
        loadSource(Object::class)
        loadSource(java.lang.String::class)
        loadSource(java.lang.Integer::class)
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("spring/RequestMethod.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/MyPostMapping.java")
        loadFile("spring/FakeMapping.java")
        loadFile("spring/RestController.java")
        loadFile("api/BaseController.java")
        myCtrlPsiClass = loadClass("api/MyCtrl.java")!!
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!
    }

    fun testResolveRequestMapping() {
        assertEquals(
            "{\"value\":\"/\"}",
            springRequestMappingResolver.resolveRequestMapping(myCtrlPsiClass).toJson()
        )
        assertEquals(
            "{\"method\":\"RequestMethod.POST\",\"value\":\"/myPost\"}",
            springRequestMappingResolver.resolveRequestMapping(myCtrlPsiClass.methods[0]).toJson()
        )
        assertEquals(
            "{\"method\":\"RequestMethod.POST\",\"params\":\"name\",\"value\":\"/myPostWithParam\"}",
            springRequestMappingResolver.resolveRequestMapping(myCtrlPsiClass.methods[1]).toJson()
        )
        assertNull(
            springRequestMappingResolver.resolveRequestMapping(myCtrlPsiClass.methods[2]).toJson()
        )
        assertEquals(
            "{\"value\":\"user\"}",
            springRequestMappingResolver.resolveRequestMapping(userCtrlPsiClass).toJson()
        )
        assertEquals(
            "{\"value\":\"/greeting\"}",
            springRequestMappingResolver.resolveRequestMapping(userCtrlPsiClass.methods[0]).toJson()
        )
        assertEquals(
            "{\"method\":\"GET\",\"value\":\"/get/{id}\"}",
            springRequestMappingResolver.resolveRequestMapping(userCtrlPsiClass.methods[1]).toJson()
        )
        assertEquals(
            "{\"method\":\"POST\",\"value\":\"/add\"}",
            springRequestMappingResolver.resolveRequestMapping(userCtrlPsiClass.methods[2]).toJson()
        )
        assertEquals(
            "{\"method\":\"PUT\",\"value\":\"/update\"}",
            springRequestMappingResolver.resolveRequestMapping(userCtrlPsiClass.methods[3]).toJson()
        )
    }
}