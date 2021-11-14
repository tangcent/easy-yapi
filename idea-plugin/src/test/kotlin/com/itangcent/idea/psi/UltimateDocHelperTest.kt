package com.itangcent.idea.psi

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case of [UltimateDocHelper]
 */
internal class UltimateDocHelperTest : PluginContextLightCodeInsightFixtureTestCase() {

    private lateinit var modelPsiClass: PsiClass
    private lateinit var defaultPsiClass: PsiClass

    @Inject
    private lateinit var ultimateDocHelper: UltimateDocHelper

    override fun beforeBind() {
        super.beforeBind()
        defaultPsiClass = loadClass("model/Default.java")!!
        modelPsiClass = loadClass("model/Model.java")!!
    }

    override fun customConfig(): String {
        return super.customConfig() +
                "\nclass.doc=groovy:\"class:\"+it.name()"
    }

    fun testFindUltimateDescOfClass() {
        assertEquals(
            "default model\n" +
                    "class:com.itangcent.model.Default", ultimateDocHelper.findUltimateDescOfClass(defaultPsiClass)
        )
        assertEquals(
            "class:com.itangcent.model.Model", ultimateDocHelper.findUltimateDescOfClass(modelPsiClass)
        )
    }
}