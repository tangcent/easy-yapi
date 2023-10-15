package com.itangcent.idea.plugin.rule

import com.itangcent.intellij.config.rule.RuleChain
import com.itangcent.intellij.config.rule.RuleMode
import kotlin.reflect.KClass

enum class MyStringRuleMode : RuleMode<String> {
    LIST {
        override fun compute(rules: RuleChain<String>): Any {
            return (rules as RuleChain<Any>)
                .mapNotNull { it() }
                .flatMap {
                    it.flatten()
                }
                .mapNotNull { it as? String }
                .filter { it.isNotEmpty() }
                .toList()
        }
    };

    override fun targetType(): KClass<String> {
        return String::class
    }

    companion object {
        private fun Any?.flatten(): List<Any?> {
            return when (this) {
                null -> {
                    emptyList()
                }

                is Array<*> -> {
                    this.toList().flatten()
                }

                is Collection<*> -> {
                    this.flatMap { it.flatten() }
                }

                else -> {
                    listOf(this)
                }
            }
        }
    }
}