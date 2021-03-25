package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.intellij.jvm.JvmClassHelper
import com.itangcent.intellij.jvm.duck.SingleDuckType
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import junit.framework.Assert

/**
 * Test case of [CommentResolver]
 * test case of rule: [com.itangcent.intellij.psi.ClassRuleKeys.ENUM_CONVERT]
 */
internal class CommentResolverTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var commentResolver: CommentResolver

    @Inject
    private lateinit var jvmClassHelper: JvmClassHelper

    private lateinit var userTypePsiClass: PsiClass
    private lateinit var userInfoPsiClass: PsiClass
    private lateinit var userCtrlPsiClass: PsiClass
    private lateinit var javaVersionPsiClass: PsiClass

    override fun setUp() {
        super.setUp()
        userTypePsiClass = loadClass("constant/UserType.java")!!
        userInfoPsiClass = loadClass("model/UserInfo.java")!!
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!
        javaVersionPsiClass = loadClass("constant/JavaVersion.java")!!
    }

    override fun customConfig(): String {
        //language=Properties
        return "json.rule.enum.convert[com.itangcent.constant.JavaVersion]=~#name"
    }

    fun testResolveCommentForType() {
        val userTypeDuckType = SingleDuckType(userTypePsiClass)
        val userInfoDuckType = SingleDuckType(userInfoPsiClass)
        val userCtrlDuckType = SingleDuckType(userCtrlPsiClass)
        val javaVersionDuckType = SingleDuckType(javaVersionPsiClass)
        Assert.assertEquals("ADMIN :administration\n" +
                "MEM :a person, an animal or a plant\n" +
                "GUEST :Anonymous visitor", commentResolver.resolveCommentForType(userTypeDuckType, userTypePsiClass))
        Assert.assertEquals(null, commentResolver.resolveCommentForType(userInfoDuckType, userInfoPsiClass))
        Assert.assertEquals(null, commentResolver.resolveCommentForType(userCtrlDuckType, userCtrlPsiClass))
        Assert.assertEquals("0.9 :The Java version reported by Android. This is not an official Java version number.\n" +
                "1.1 :Java 1.1.\n" +
                "1.2 :Java 1.2.\n" +
                "1.3 :Java 1.3.\n" +
                "1.4 :Java 1.4.\n" +
                "1.5 :Java 1.5.\n" +
                "1.6 :Java 1.6.\n" +
                "1.7 :Java 1.7.\n" +
                "1.8 :Java 1.8.\n" +
                "9 :Java 1.9.\n" +
                "9 :Java 9\n" +
                "10 :Java 10\n" +
                "11 :Java 11\n" +
                "12 :Java 12\n" +
                "13 :Java 13", commentResolver.resolveCommentForType(javaVersionDuckType, javaVersionPsiClass))

        val userTypePsiType = jvmClassHelper.resolveClassToType(userTypePsiClass)!!
        val userInfoPsiType = jvmClassHelper.resolveClassToType(userInfoPsiClass)!!
        val userCtrlPsiType = jvmClassHelper.resolveClassToType(userCtrlPsiClass)!!
        val javaVersionPsiType = jvmClassHelper.resolveClassToType(javaVersionPsiClass)!!
        Assert.assertEquals("ADMIN :administration\n" +
                "MEM :a person, an animal or a plant\n" +
                "GUEST :Anonymous visitor", commentResolver.resolveCommentForType(userTypePsiType, userTypePsiClass))
        Assert.assertEquals(null, commentResolver.resolveCommentForType(userInfoPsiType, userInfoPsiClass))
        Assert.assertEquals(null, commentResolver.resolveCommentForType(userCtrlPsiType, userCtrlPsiClass))
        Assert.assertEquals("0.9 :The Java version reported by Android. This is not an official Java version number.\n" +
                "1.1 :Java 1.1.\n" +
                "1.2 :Java 1.2.\n" +
                "1.3 :Java 1.3.\n" +
                "1.4 :Java 1.4.\n" +
                "1.5 :Java 1.5.\n" +
                "1.6 :Java 1.6.\n" +
                "1.7 :Java 1.7.\n" +
                "1.8 :Java 1.8.\n" +
                "9 :Java 1.9.\n" +
                "9 :Java 9\n" +
                "10 :Java 10\n" +
                "11 :Java 11\n" +
                "12 :Java 12\n" +
                "13 :Java 13", commentResolver.resolveCommentForType(javaVersionPsiType, javaVersionPsiClass))


    }
}