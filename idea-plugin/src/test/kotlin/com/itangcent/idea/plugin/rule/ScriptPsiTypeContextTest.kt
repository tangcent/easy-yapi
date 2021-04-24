package com.itangcent.idea.plugin.rule

import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.idea.plugin.rule.ScriptRuleParser.ScriptClassContext
import com.itangcent.idea.plugin.rule.ScriptRuleParser.ScriptPsiTypeContext

/**
 * Test case of [ScriptPsiTypeContext]
 */
class ScriptPsiTypeContextTest : ScriptClassContextBaseTest() {

    override fun PsiClass.asClassContext(): ScriptClassContext {
        return ruleParser.contextOf(PsiTypesUtil.getClassType(this), this) as ScriptClassContext
    }

}