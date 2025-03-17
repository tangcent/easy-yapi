package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.api.infer.DefaultMethodInferHelper
import com.itangcent.idea.plugin.api.infer.MethodInferHelper
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.util.*

/**
 * Test case of [DefaultMethodInferHelper]
 */
internal class DefaultMethodInferHelperTest : PluginContextLightCodeInsightFixtureTestCase() {

    private lateinit var inferDemoCtrlPsiClass: PsiClass

    @Inject
    private lateinit var methodInferHelper: MethodInferHelper

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(MethodInferHelper::class) { it.with(DefaultMethodInferHelper::class) }
    }

    override fun setUp() {
        super.setUp()
        loadSource(Object::class)
        loadSource(Collection::class)
        loadSource(Map::class)
        loadSource(List::class)
        loadSource(LinkedList::class)
        loadSource(HashMap::class)
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        inferDemoCtrlPsiClass = loadClass("api/InferDemoCtrl.java")!!
    }

    fun testInfer() {
        var inferReturn: Any? = null
        for (i in 0..2) {
            try {
                inferReturn = methodInferHelper.inferReturn(inferDemoCtrlPsiClass.methods[0])
                break
            } catch (e: Throwable) {
                if (e.message?.contains("Stub index points to a file without PSI") != true) {
                    throw e
                }
            }
        }
        assertEquals(
            "{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"success\",\"data\":[{\"key1\":\"string\",\"@comment\":{\"key1\":\"This is the key for the test\",\"key2\":\"This is a test key valued 666\",\"key3\":\"This is a child for test\"},\"key2\":666,\"key3\":{\"subKey\":\"string\",\"@comment\":{\"subKey\":\"This is the key of the child\"}}},{}]}",
            GsonUtils.toJson(inferReturn)
        )
    }
}