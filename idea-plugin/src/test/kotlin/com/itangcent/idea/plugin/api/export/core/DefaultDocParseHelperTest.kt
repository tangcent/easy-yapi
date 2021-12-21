package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.DocHelper
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case of [DefaultDocParseHelper]
 */
internal class DefaultDocParseHelperTest : PluginContextLightCodeInsightFixtureTestCase() {

    private lateinit var linkCasePsiClass: PsiClass
    private lateinit var userInfoPsiClass: PsiClass
    private lateinit var modelPsiClass: PsiClass
    private lateinit var nestedClassPsiClass: PsiClass
    private lateinit var nestedClassBPsiClass: PsiClass
    private lateinit var myInnerClassPsiClass: PsiClass
    private lateinit var innerClassAPsiClass: PsiClass
    private lateinit var staticInnerClassAPsiClass: PsiClass
    private lateinit var innerClassBPsiClass: PsiClass
    private lateinit var staticInnerClassBPsiClass: PsiClass
    private lateinit var numbersPsiClass: PsiClass
    private lateinit var javaVersionPsiClass: PsiClass

    @Inject
    private lateinit var defaultDocParseHelper: DefaultDocParseHelper

    @Inject
    private lateinit var docHelper: DocHelper

    private var linked: Any? = null

    override fun beforeBind() {
        linkCasePsiClass = loadClass("cases/LinkCase.java")!!
        userInfoPsiClass = loadClass("model/UserInfo.java")!!
        modelPsiClass = loadClass("model/Model.java")!!
        nestedClassPsiClass = loadClass("cases/NestedClass.java")!!
        nestedClassBPsiClass = loadClass("cases/NestedClassB.java")!!
        numbersPsiClass = loadClass("constant/Numbers.java")!!
        javaVersionPsiClass = loadClass("constant/JavaVersion.java")!!
        myInnerClassPsiClass = linkCasePsiClass.allInnerClasses.first()

        innerClassAPsiClass = nestedClassPsiClass.allInnerClasses.first()
        staticInnerClassAPsiClass = nestedClassPsiClass.allInnerClasses.last()

        innerClassBPsiClass = nestedClassBPsiClass.allInnerClasses.first()
        staticInnerClassBPsiClass = nestedClassBPsiClass.allInnerClasses.last()
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(DocParseHelper::class) {
            it.with(DefaultDocParseHelper::class)
        }
        builder.bind(LinkResolver::class) {
            it.toInstance(object : LinkResolver {
                override fun linkToClass(linkClass: Any): String {
                    linked = linkClass
                    return linkClass.toString()
                }

                override fun linkToMethod(linkMethod: Any): String {
                    linked = linkMethod
                    return linkMethod.toString()
                }

                override fun linkToProperty(linkField: Any): String {
                    linked = linkField
                    return linkField.toString()
                }
            })
        }
    }

    fun testResolveLinkInAttr() {
        val methods = linkCasePsiClass.methods

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[0], "see"), linkCasePsiClass)
        assertEquals(
            userInfoPsiClass, linked
        )
        linked = null

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[0], "return"), linkCasePsiClass)
        assertEquals(
            userInfoPsiClass, linked
        )
        linked = null


        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[1], "see"), linkCasePsiClass)
        assertEquals(
            myInnerClassPsiClass, linked
        )
        linked = null

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[1], "return"), linkCasePsiClass)
        assertEquals(
            myInnerClassPsiClass, linked
        )
        linked = null

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[2], "see"), linkCasePsiClass)
        assertEquals(
            staticInnerClassAPsiClass, linked
        )
        linked = null

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[2], "return"), linkCasePsiClass)
        assertEquals(
            innerClassAPsiClass, linked
        )
        linked = null

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[3], "see"), linkCasePsiClass)
        assertEquals(
            staticInnerClassBPsiClass, linked
        )
        linked = null

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[3], "return"), linkCasePsiClass)
        assertEquals(
            innerClassBPsiClass, linked
        )
        linked = null

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[4], "see"), linkCasePsiClass)
        assertEquals(
            modelPsiClass, linked
        )
        linked = null

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[4], "return"), linkCasePsiClass)
        assertEquals(
            modelPsiClass, linked
        )
        linked = null

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[5], "see"), linkCasePsiClass)
        assertEquals(
            numbersPsiClass.allFields[0], linked
        )
        linked = null

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[5], "return"), linkCasePsiClass)
        assertEquals(
            javaVersionPsiClass.allFields[0], linked
        )
        linked = null

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[6], "see"), linkCasePsiClass)
        assertEquals(
            modelPsiClass.allMethods[0], linked
        )
        linked = null

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[6], "return"), linkCasePsiClass)
        assertEquals(
            userInfoPsiClass.allMethods[0], linked
        )
        linked = null

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[7], "see"), linkCasePsiClass)
        assertEquals(
            modelPsiClass.allFields[0], linked
        )
        linked = null

        defaultDocParseHelper.resolveLinkInAttr(docHelper.findDocByTag(methods[7], "return"), linkCasePsiClass)
        assertEquals(
            userInfoPsiClass.allFields[0], linked
        )
        linked = null

    }
}