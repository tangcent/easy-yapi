package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.kit.toJson
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case of [CustomSpringRequestMappingResolver]
 */
internal class CustomSpringRequestMappingResolverTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var customSpringRequestMappingResolver: CustomSpringRequestMappingResolver

    private lateinit var myCtrlPsiClass: PsiClass

    override fun beforeBind() {
        super.beforeBind()
        loadSource(Object::class)
        loadSource(java.lang.String::class)
        loadSource(java.lang.Integer::class)
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("spring/RequestMethod.java")
        loadFile("spring/MyPostMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/FakeMapping.java")
        loadFile("spring/RestController.java")
        loadFile("api/BaseController.java")
        myCtrlPsiClass = loadClass("api/MyCtrl.java")!!
    }

    fun testResolveRequestMapping() {
        assertEquals(
            "{\"method\":\"RequestMethod.POST\",\"value\":\"/myPost\"}",
            customSpringRequestMappingResolver.resolveRequestMapping(myCtrlPsiClass.methods[0]).toJson()
        )
        assertEquals(
            "{\"method\":\"RequestMethod.POST\",\"params\":\"name\",\"value\":\"/myPostWithParam\"}",
            customSpringRequestMappingResolver.resolveRequestMapping(myCtrlPsiClass.methods[1]).toJson()
        )
        assertNull(
            customSpringRequestMappingResolver.resolveRequestMapping(myCtrlPsiClass.methods[2]).toJson()
        )
    }
}