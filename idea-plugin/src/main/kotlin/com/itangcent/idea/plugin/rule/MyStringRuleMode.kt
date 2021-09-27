package com.itangcent.idea.plugin.rule

import com.itangcent.common.utils.asStream
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.intellij.config.rule.RuleChain
import com.itangcent.intellij.config.rule.RuleMode
import kotlin.reflect.KClass
import kotlin.streams.toList

enum class MyStringRuleMode : RuleMode<String> {
    LIST {
        override fun compute(rules: RuleChain<String>): Any? {
            return rules
                .asStream()
                .map { it.compute() }
                .filter { it.notNullOrEmpty() }
                .toList()
        }
    };

    override fun targetType(): KClass<String> {
        return String::class
    }
}