package com.itangcent.idea.plugin.rule

import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.config.rule.RuleContext
import com.itangcent.intellij.config.rule.RuleKey
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.jvm.element.ExplicitElement


fun <T> RuleComputer.computer(ruleKey: RuleKey<T>, explicitElement: ExplicitElement<*>): T? {
    return this.computer(ruleKey, explicitElement, explicitElement.psi())
}


fun RuleParser.contextOf(context: ExplicitElement<*>): RuleContext {
    return this.contextOf(context, context.psi())
}