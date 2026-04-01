package com.itangcent.easyapi.rule.engine

import com.intellij.psi.PsiElement

/**
 * Listener interface for rule computation events.
 *
 * Implementations can observe rule evaluation results,
 * useful for caching, logging, or debugging.
 *
 * ## Usage
 * ```kotlin
 * class MyListener : RuleComputeListener {
 *     override fun onRuleComputed(key: String, element: PsiElement?, result: Any?) {
 *         println("Rule $key evaluated to $result")
 *     }
 * }
 * ```
 *
 * @see RuleComputeListenerRegistry for listener registration
 */
interface RuleComputeListener {
    /**
     * Called when a rule is evaluated.
     *
     * @param key The rule key
     * @param element The PSI element context (or null for global rules)
     * @param result The evaluation result
     */
    fun onRuleComputed(key: String, element: PsiElement?, result: Any?)
}
