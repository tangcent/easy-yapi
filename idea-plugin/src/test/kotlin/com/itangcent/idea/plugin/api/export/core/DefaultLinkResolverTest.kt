package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case of [DefaultLinkResolver]
 */
internal class DefaultLinkResolverTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var linkResolver: LinkResolver

    private lateinit var userInfoPsiClass: PsiClass
    private lateinit var resultPsiClass: PsiClass

    override fun setUp() {
        super.setUp()
        userInfoPsiClass = createClass("model/UserInfo.java")!!
        resultPsiClass = createClass("model/Result.java")!!
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)

        builder.bind(LinkResolver::class) { it.with(DefaultLinkResolver::class) }
    }

    fun testLinkToClass() {
        assertEquals("[UserInfo(user info)]", linkResolver.linkToClass(userInfoPsiClass))
        assertEquals("[PsiMethod:getId]", linkResolver.linkToClass(userInfoPsiClass.methods[0]))
        assertEquals("[PsiField:id]", linkResolver.linkToClass(userInfoPsiClass.fields[0]))
    }

    fun testLinkToMethod() {
        assertEquals("[PsiClass:UserInfo]", linkResolver.linkToMethod(userInfoPsiClass))
        assertEquals(
            "[com.itangcent.model.UserInfo#id(user id)]",
            linkResolver.linkToMethod(userInfoPsiClass.methods[0])
        )
        assertEquals(
            "[com.itangcent.model.Result#success(T)(create success result)]",
            linkResolver.linkToMethod(resultPsiClass.methods.first { it.name == "success" })
        )
        assertEquals("[PsiField:id]", linkResolver.linkToMethod(userInfoPsiClass.fields[0]))
    }

    fun testLinkToProperty() {
        assertEquals("[PsiClass:UserInfo]", linkResolver.linkToProperty(userInfoPsiClass))
        assertEquals("[PsiMethod:getId]", linkResolver.linkToProperty(userInfoPsiClass.methods[0]))
        assertEquals(
            "[com.itangcent.model.UserInfo#id(user id)]",
            linkResolver.linkToProperty(userInfoPsiClass.fields[0])
        )
    }

}