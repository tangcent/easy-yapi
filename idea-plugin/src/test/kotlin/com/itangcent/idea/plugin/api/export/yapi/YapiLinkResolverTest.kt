package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.idea.plugin.api.export.LinkResolver
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.testFramework.ContextLightCodeInsightFixtureTestCase
import junit.framework.Assert
import org.mockito.Mockito

/**
 * Test case of [YapiLinkResolver]
 */
internal class YapiLinkResolverTest : ContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var linkResolver: LinkResolver

    private lateinit var userInfoPsiClass: PsiClass
    private lateinit var userCtrlPsiClass: PsiClass
    private lateinit var testCtrlPsiClass: PsiClass

    override fun setUp() {
        super.setUp()
        userInfoPsiClass = createClass("model/UserInfo.java")!!
        userCtrlPsiClass = createClass("api/UserCtrl.java")!!
        testCtrlPsiClass = createClass("api/TestCtrl.java")!!
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        builder.bind(LinkResolver::class) { it.with(YapiLinkResolver::class) }
        val mockYapiApiHelper = Mockito.mock(YapiApiHelper::class.java)
        Mockito.`when`(mockYapiApiHelper.findCartWeb(Mockito.anyString(), Mockito.anyString()))
                .thenReturn("http://yapi.itangcent.com/project/123/interface/api/cat_2345")
        Mockito.`when`(mockYapiApiHelper.getApiWeb(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn("http://yapi.itangcent.com/project/123/interface/api/1234")
        builder.bind(YapiApiHelper::class) { it.toInstance(mockYapiApiHelper) }
    }

    fun testLinkToClass() {
        Assert.assertEquals("[<a href=\"http://yapi.itangcent.com/project/123/interface/api/cat_2345\">apis about user</a>]", linkResolver.linkToClass(userCtrlPsiClass))
        Assert.assertEquals("[<a href=\"http://yapi.itangcent.com/project/123/interface/api/cat_2345\">test apis</a>]", linkResolver.linkToClass(testCtrlPsiClass))
        Assert.assertEquals("[PsiMethod:greeting]", linkResolver.linkToClass(userCtrlPsiClass.methods[0]))
        Assert.assertEquals("[PsiField:id]", linkResolver.linkToClass(userInfoPsiClass.fields[0]))
    }

    fun testLinkToMethod() {
        Assert.assertEquals("[PsiClass:UserCtrl]", linkResolver.linkToMethod(userCtrlPsiClass))
        Assert.assertEquals("[<a href=\"http://yapi.itangcent.com/project/123/interface/api/1234\">say hello</a>]", linkResolver.linkToMethod(userCtrlPsiClass.methods[0]))
        Assert.assertEquals("[<a href=\"http://yapi.itangcent.com/project/123/interface/api/1234\">get user info</a>]", linkResolver.linkToMethod(userCtrlPsiClass.methods[1]))
        Assert.assertEquals("[PsiField:id]", linkResolver.linkToMethod(userInfoPsiClass.fields[0]))
    }

    fun testLinkToProperty() {
        Assert.assertEquals("[PsiClass:UserCtrl]", linkResolver.linkToProperty(userCtrlPsiClass))
        Assert.assertEquals("[PsiMethod:greeting]", linkResolver.linkToProperty(userCtrlPsiClass.methods[0]))
        Assert.assertEquals("[user id]", linkResolver.linkToProperty(userInfoPsiClass.fields[0]))
    }

}