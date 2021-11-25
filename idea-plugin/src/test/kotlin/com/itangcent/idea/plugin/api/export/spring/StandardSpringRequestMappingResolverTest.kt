package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.kit.toJson
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case of [StandardSpringRequestMappingResolver]
 */
internal class StandardSpringRequestMappingResolverTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var standardSpringRequestMappingResolver: StandardSpringRequestMappingResolver

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
        assertNull(
            standardSpringRequestMappingResolver.resolveRequestMapping(myCtrlPsiClass).toJson()
        )
        assertNull(
            standardSpringRequestMappingResolver.resolveRequestMapping(myCtrlPsiClass.methods[0]).toJson()
        )
        assertNull(
            standardSpringRequestMappingResolver.resolveRequestMapping(myCtrlPsiClass.methods[1]).toJson()
        )
        assertNull(
            standardSpringRequestMappingResolver.resolveRequestMapping(myCtrlPsiClass.methods[2]).toJson()
        )
        assertEquals(
            "{\"value\":\"user\"}",
            standardSpringRequestMappingResolver.resolveRequestMapping(userCtrlPsiClass).toJson()
        )
        assertEquals(
            "{\"value\":\"/greeting\"}",
            standardSpringRequestMappingResolver.resolveRequestMapping(userCtrlPsiClass.methods[0]).toJson()
        )
        assertEquals(
            "{\"method\":\"GET\",\"value\":\"/get/{id}\"}",
            standardSpringRequestMappingResolver.resolveRequestMapping(userCtrlPsiClass.methods[1]).toJson()
        )
        assertEquals(
            "{\"method\":\"POST\",\"value\":\"/add\"}",
            standardSpringRequestMappingResolver.resolveRequestMapping(userCtrlPsiClass.methods[2]).toJson()
        )
        assertEquals(
            "{\"method\":\"PUT\",\"value\":\"/update\"}",
            standardSpringRequestMappingResolver.resolveRequestMapping(userCtrlPsiClass.methods[3]).toJson()
        )
    }
}