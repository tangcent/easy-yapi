package com.itangcent.idea.plugin.rule

import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.intellij.config.rule.RuleChain
import com.itangcent.intellij.config.rule.RuleMode
import com.itangcent.intellij.config.rule.asSequence
import kotlin.reflect.KClass

enum class MyStringRuleMode : RuleMode<String> {
    LIST {
        override fun compute(rules: RuleChain<String>): Any? {
            return rules
                .asSequence()
                .map { it.compute() }
                .filter { it.notNullOrEmpty() }
                .toList()
        }
    };

    override fun targetType(): KClass<String> {
        return String::class
    }
}