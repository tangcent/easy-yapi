package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.idea.plugin.rule.ScriptRuleParser.ScriptClassContext
import com.itangcent.idea.plugin.rule.ScriptRuleParser.ScriptExplicitClassContext
import com.itangcent.intellij.jvm.DuckTypeHelper
import com.itangcent.intellij.jvm.duck.DuckType

/**
 * Test case of [ScriptExplicitClassContext]
 */
class ScriptDuckTypeContextTest : ScriptClassContextBaseTest() {

    @Inject
    private lateinit var duckTypeHelper: DuckTypeHelper

    private fun DuckType.asScriptContext(): ScriptClassContext {
        return ruleParser.contextOf(this, modelPsiClass) as ScriptClassContext
    }

    override fun PsiClass.asClassContext(): ScriptClassContext {
        return duckTypeHelper.ensureType(PsiTypesUtil.getClassType(this))!!.asScriptContext()
    }

    fun testIsArrayForDuckType() {
        assertTrue(duckTypeHelper.resolve("com.itangcent.model.Model[]", modelPsiClass)!!
                .asScriptContext().isArray())
    }

    fun testIsCollectionForDuckType() {
        assertTrue(duckTypeHelper.resolve("java.util.Collection<com.itangcent.model.Model[]>", modelPsiClass)!!
                .asScriptContext().isCollection())
        assertTrue(duckTypeHelper.resolve("java.util.List<com.itangcent.model.Model[]>", modelPsiClass)!!
                .asScriptContext().isCollection())
        assertFalse(duckTypeHelper.resolve("java.util.Map<java.lang.String,com.itangcent.model.Model[]>", modelPsiClass)!!
                .asScriptContext().isCollection())
    }

    fun testIsMapForDuckType() {
        assertFalse(duckTypeHelper.resolve("java.util.Collection<com.itangcent.model.Model[]>", modelPsiClass)!!
                .asScriptContext().isMap())
        assertFalse(duckTypeHelper.resolve("java.util.List<com.itangcent.model.Model[]>", modelPsiClass)!!
                .asScriptContext().isMap())
        assertTrue(duckTypeHelper.resolve("java.util.Map<java.lang.String,com.itangcent.model.Model[]>", modelPsiClass)!!
                .asScriptContext().isMap())
    }

}