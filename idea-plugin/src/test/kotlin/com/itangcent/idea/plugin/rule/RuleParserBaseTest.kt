package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import kotlin.reflect.KClass

abstract class RuleParserBaseTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    protected lateinit var ruleParser: RuleParser

    protected lateinit var userCtrlPsiClass: PsiClass

    protected lateinit var modelPsiClass: PsiClass

    protected lateinit var listPsiClass: PsiClass

    protected lateinit var greetingPsiMethod: PsiMethod

    protected lateinit var getUserInfoPsiMethod: PsiMethod

    override fun beforeBind() {
        super.beforeBind()
        loadSource(java.lang.Deprecated::class)
        loadSource(Object::class.java)!!
        loadSource(Collection::class.java)!!
        listPsiClass = loadSource(List::class.java)!!
        loadClass("annotation/Public.java")
        loadClass("spring/RequestMapping.java")
        loadClass("spring/GetMapping.java")
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!
        modelPsiClass = loadClass("model/Model.java")!!
        greetingPsiMethod = userCtrlPsiClass.methods[0]
        getUserInfoPsiMethod = userCtrlPsiClass.methods[1]
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(RuleParser::class) { it.with(ruleParserClass()) }
    }

    protected abstract fun ruleParserClass(): KClass<out RuleParser>

}