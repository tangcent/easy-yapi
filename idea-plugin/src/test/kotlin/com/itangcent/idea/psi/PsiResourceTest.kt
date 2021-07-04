package com.itangcent.idea.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.common.model.Doc
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case of [PsiResource]
 */
internal class PsiResourceTest : PluginContextLightCodeInsightFixtureTestCase() {

    private lateinit var modelPsiClass: PsiClass
    private lateinit var psiMethod: PsiMethod

    override fun beforeBind() {
        super.beforeBind()
        modelPsiClass = loadClass("model/Model.java")!!
        psiMethod = modelPsiClass.methods.first()
    }

    fun testResourceClass() {
        assertNull(Doc().resourceClass())
        assertNull(Doc().also { it.resource = "string" }.resourceClass())
        assertNull(Doc().also { it.resource = 1024 }.resourceClass())
        assertEquals(modelPsiClass, Doc().also { it.resource = modelPsiClass }.resourceClass())
        assertEquals(modelPsiClass, Doc().also { it.resource = PsiClassResource(modelPsiClass) }.resourceClass())
        assertEquals(modelPsiClass, Doc().also { it.resource = psiMethod }.resourceClass())
        assertEquals(
            modelPsiClass,
            Doc().also { it.resource = PsiMethodResource(psiMethod, modelPsiClass) }.resourceClass()
        )
    }

    fun testResource() {
        assertNull(Doc().resource())
        assertNull(Doc().also { it.resource = "string" }.resource())
        assertNull(Doc().also { it.resource = 1024 }.resource())
        assertEquals(modelPsiClass, Doc().also { it.resource = modelPsiClass }.resource())
        assertEquals(modelPsiClass, Doc().also { it.resource = PsiClassResource(modelPsiClass) }.resource())
        assertEquals(psiMethod, Doc().also { it.resource = psiMethod }.resource())
        assertEquals(
            psiMethod,
            Doc().also { it.resource = PsiMethodResource(psiMethod, modelPsiClass) }.resource()
        )
    }

    fun testResourceMethod() {
        assertNull(Doc().resourceMethod())
        assertNull(Doc().also { it.resource = "string" }.resourceMethod())
        assertNull(Doc().also { it.resource = 1024 }.resourceMethod())
        assertNull(Doc().also { it.resource = modelPsiClass }.resourceMethod())
        assertNull(Doc().also { it.resource = PsiClassResource(modelPsiClass) }.resourceMethod())
        assertEquals(psiMethod, Doc().also { it.resource = psiMethod }.resourceMethod())
        assertEquals(
            psiMethod,
            Doc().also { it.resource = PsiMethodResource(psiMethod, modelPsiClass) }.resourceMethod()
        )
    }
}