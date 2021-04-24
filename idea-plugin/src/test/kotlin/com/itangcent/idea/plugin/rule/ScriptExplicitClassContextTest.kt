package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.idea.plugin.rule.ScriptRuleParser.ScriptClassContext
import com.itangcent.idea.plugin.rule.ScriptRuleParser.ScriptExplicitClassContext
import com.itangcent.intellij.jvm.DuckTypeHelper

/**
 * Test case of [ScriptExplicitClassContext]
 */
class ScriptExplicitClassContextTest : ScriptClassContextBaseTest() {

    @Inject
    private lateinit var duckTypeHelper: DuckTypeHelper

    override fun PsiClass.asClassContext(): ScriptClassContext {
        return ruleParser.contextOf(duckTypeHelper.explicit(this), this) as ScriptClassContext
    }

}