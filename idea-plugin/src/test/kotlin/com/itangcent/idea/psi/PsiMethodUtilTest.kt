package com.itangcent.idea.psi

import com.intellij.psi.PsiClass
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import org.junit.Assert

/**
 * Test case of [PsiMethodUtil]
 */
internal class PsiMethodUtilTest : PluginContextLightCodeInsightFixtureTestCase() {

    private lateinit var resultPsiClass: PsiClass
    private lateinit var iResultPsiClass: PsiClass

    override fun beforeBind() {
        super.beforeBind()

        iResultPsiClass = loadClass("model/IResult.java")!!
        resultPsiClass = loadClass("model/Result.java")!!
    }

    fun testIsSuperMethod() {
        assertTrue(PsiMethodUtil.isSuperMethod(
            resultPsiClass.methods.first { it.name == "getCode" },
            iResultPsiClass.methods.first { it.name == "getCode" }
        ))
        assertFalse(PsiMethodUtil.isSuperMethod(
            resultPsiClass.methods.first { it.name == "fail" },
            resultPsiClass.methods.last { it.name == "fail" }
        ))
    }
}