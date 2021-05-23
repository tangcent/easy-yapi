package com.itangcent.idea.plugin.rule

import com.itangcent.intellij.config.rule.RuleContext
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.jvm.element.ExplicitElement


fun RuleParser.contextOf(context: ExplicitElement<*>): RuleContext {
    return this.contextOf(context, context.psi())
}