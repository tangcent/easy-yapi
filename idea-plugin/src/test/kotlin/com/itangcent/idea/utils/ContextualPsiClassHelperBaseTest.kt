package com.itangcent.idea.utils

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.intellij.config.rule.RuleComputeListener
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.Collection

/**
 * Base test case of [ContextualPsiClassHelper]
 */
internal abstract class ContextualPsiClassHelperBaseTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    protected lateinit var psiClassHelper: PsiClassHelper

    protected lateinit var objectPsiClass: PsiClass
    protected lateinit var integerPsiClass: PsiClass
    protected lateinit var stringPsiClass: PsiClass
    protected lateinit var collectionPsiClass: PsiClass
    protected lateinit var listPsiClass: PsiClass
    protected lateinit var mapPsiClass: PsiClass
    protected lateinit var hashMapPsiClass: PsiClass
    protected lateinit var linkedListPsiClass: PsiClass
    protected lateinit var modelPsiClass: PsiClass
    protected lateinit var userInfoPsiClass: PsiClass
    protected lateinit var defaultPsiClass: PsiClass
    protected lateinit var javaVersionPsiClass: PsiClass
    protected lateinit var numbersPsiClass: PsiClass

    override fun beforeBind() {
        super.beforeBind()
        loadFile("annotation/JsonProperty.java")!!
        objectPsiClass = loadSource(Object::class.java)!!
        integerPsiClass = loadSource(java.lang.Integer::class)!!
        loadSource(java.lang.Long::class)!!
        stringPsiClass = loadSource(java.lang.String::class)!!
        collectionPsiClass = loadSource(Collection::class.java)!!
        mapPsiClass = loadSource(java.util.Map::class.java)!!
        listPsiClass = loadSource(java.util.List::class.java)!!
        hashMapPsiClass = loadSource(java.util.HashMap::class.java)!!
        linkedListPsiClass = loadSource(LinkedList::class.java)!!
        loadClass("validation/NotBlank.java")
        loadClass("validation/NotNull.java")
        loadSource(LocalDate::class)
        loadSource(LocalDateTime::class)
        modelPsiClass = loadClass("model/Model.java")!!
        userInfoPsiClass = loadClass("model/UserInfo.java")!!
        defaultPsiClass = loadClass("model/Default.java")!!
        javaVersionPsiClass = loadClass("constant/JavaVersion.java")!!
        numbersPsiClass = loadClass("constant/Numbers.java")!!
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(RuleComputeListener::class) { it.with(RuleComputeListenerRegistry::class) }
    }
}