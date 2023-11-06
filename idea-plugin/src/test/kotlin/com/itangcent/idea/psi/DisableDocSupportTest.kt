package com.itangcent.idea.psi

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.DocHelper
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case of [com.itangcent.idea.psi.DisableDocSupport]
 */
abstract class DisableDocSupportTest : PluginContextLightCodeInsightFixtureTestCase() {

    abstract val disableDoc: Boolean

    protected lateinit var userInfoPsiClass: PsiClass
    protected lateinit var userCtrlPsiClass: PsiClass

    @Inject
    protected lateinit var docHelper: DocHelper

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        DisableDocSupport.bind(builder)

        userInfoPsiClass = loadClass("model/UserInfo.java")!!
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!
    }

    override fun customConfig(): String? {
        return super.customConfig() + "\n" +
                "doc.source.disable=$disableDoc"
    }

    class DisableDocSupportWithConfigTrueTest : DisableDocSupportTest() {
        override val disableDoc: Boolean = true

        fun testGetDoc() {
            assertEmpty(docHelper.getAttrOfDocComment(userInfoPsiClass))

            val fields = userInfoPsiClass.findFieldByName("name", false)
            assertEmpty(docHelper.getAttrOfDocComment(fields!!))
            assertTrue(docHelper.getTagMapOfDocComment(fields).isEmpty())
            assertEmpty(docHelper.findDocByTag(fields, "mock"))
            assertFalse(docHelper.hasTag(fields, "mock"))

            val method = userCtrlPsiClass.findMethodsByName("get", false).first()
            assertEmpty(docHelper.getAttrOfDocComment(method!!))
            assertTrue(docHelper.getTagMapOfDocComment(method).isEmpty())
            assertTrue(docHelper.getSubTagMapOfDocComment(method, "param").isEmpty())
            assertEmpty(docHelper.findDocByTag(method, "folder"))
            assertEmpty(docHelper.findDocsByTagAndName(method, "param", "id"))
        }
    }

    class DisableDocSupportWithConfigFalseTest : DisableDocSupportTest() {
        override val disableDoc: Boolean = false

        fun testGetDoc() {
            assertEquals(docHelper.getAttrOfDocComment(userInfoPsiClass), "user info")

            val fields = userInfoPsiClass.findFieldByName("name", false)
            assertEquals(docHelper.getAttrOfDocComment(fields!!), "user name")
            assertEquals(docHelper.getTagMapOfDocComment(fields), mapOf("default" to "tangcent", "mock" to "tangcent"))
            assertEquals(docHelper.findDocByTag(fields, "mock"), "tangcent")
            assertTrue(docHelper.hasTag(fields, "mock"))

            val method = userCtrlPsiClass.findMethodsByName("get", false).first()
            assertEquals(docHelper.getAttrOfDocComment(method!!), "get user info")
            assertEquals(
                docHelper.getTagMapOfDocComment(method),
                mapOf("folder" to "update-apis", "param" to "id user id", "undone" to "")
            )
            assertEquals(docHelper.getSubTagMapOfDocComment(method, "param"), mapOf("id" to "user id"))
            assertEquals(docHelper.findDocByTag(method, "folder"), "update-apis")
            assertEquals(docHelper.findDocsByTagAndName(method, "param", "id"), "user id")
        }
    }
}