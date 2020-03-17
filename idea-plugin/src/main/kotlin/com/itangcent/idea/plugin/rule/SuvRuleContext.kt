package com.itangcent.idea.plugin.rule

import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.itangcent.common.model.Extensible
import com.itangcent.intellij.config.rule.RuleContext

class SuvRuleContext : Extensible(), RuleContext {

    override fun asPsiDocCommentOwner(): PsiDocCommentOwner? {
        return null
    }

    override fun asPsiModifierListOwner(): PsiModifierListOwner? {
        return null
    }

    override fun getName(): String? {
        return null
    }

    override fun getResource(): PsiElement? {
        return null
    }

}