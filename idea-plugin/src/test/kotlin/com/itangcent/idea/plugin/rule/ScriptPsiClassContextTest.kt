package com.itangcent.idea.plugin.rule

import com.intellij.psi.PsiClass
import com.itangcent.idea.plugin.rule.ScriptRuleParser.ScriptClassContext
import com.itangcent.idea.plugin.rule.ScriptRuleParser.ScriptPsiClassContext

/**
 * Test case of [ScriptPsiClassContext]
 */
class ScriptPsiClassContextTest : ScriptClassContextBaseTest() {

    override fun PsiClass.asClassContext(): ScriptClassContext {
        return ruleParser.contextOf(this, this) as ScriptClassContext
    }

}