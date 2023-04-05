package com.itangcent.idea.psi

import com.intellij.psi.PsiClass
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import org.junit.Assert

/**
 * Test case of [PsiMethodSet]
 */
internal class PsiMethodSetTest : PluginContextLightCodeInsightFixtureTestCase() {

    private lateinit var resultPsiClass: PsiClass
    private lateinit var iResultPsiClass: PsiClass

    override fun beforeBind() {
        super.beforeBind()

        iResultPsiClass = loadClass("model/IResult.java")!!
        resultPsiClass = loadClass("model/Result.java")!!
    }

    fun testAdd() {
        val psiMethodSet = PsiMethodSet()
        var cnt = 0
        var duplicateCnt = 0
        for (psiMethod in resultPsiClass.allMethods) {
            if (psiMethodSet.add(psiMethod)) {
                ++cnt
            }else{
                ++duplicateCnt
            }
        }
        assertEquals(11, cnt)
        assertEquals(2, duplicateCnt)

        for (method in resultPsiClass.methods) {
            assertFalse(psiMethodSet.add(method))
        }
        for (method in iResultPsiClass.methods) {
            assertFalse(psiMethodSet.add(method))
        }
    }
}