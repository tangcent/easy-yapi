package com.itangcent.idea.utils

import com.google.inject.Singleton
import com.intellij.psi.PsiElement
import com.itangcent.intellij.config.rule.RuleComputeListener
import com.itangcent.intellij.config.rule.RuleContext
import com.itangcent.intellij.config.rule.RuleKey
import java.util.*

@Singleton
class RuleComputeListenerRegistry : RuleComputeListener {

    private val ruleComputeListenerChain: LinkedList<RuleComputeListener> = LinkedList()

    fun register(ruleComputeListener: RuleComputeListener) {
        ruleComputeListenerChain.add(ruleComputeListener)
    }

    override fun computer(ruleKey: RuleKey<*>, target: Any, context: PsiElement?, contextHandle: (RuleContext) -> Unit, methodHandle: (RuleKey<*>, Any, PsiElement?, (RuleContext) -> Unit) -> Any?): Any? {
        if (ruleComputeListenerChain.isEmpty()) {
            return methodHandle(ruleKey, target, context, contextHandle)
        }
        val listenerIterator = ruleComputeListenerChain.iterator()
        return computerListener(listenerIterator, ruleKey, target, context, contextHandle, methodHandle)
    }

    private fun computerListener(listenerIterator: Iterator<RuleComputeListener>, ruleKey: RuleKey<*>, target: Any, context: PsiElement?, contextHandle: (RuleContext) -> Unit, methodHandle: (RuleKey<*>, Any, PsiElement?, (RuleContext) -> Unit) -> Any?): Any? {
        return if (listenerIterator.hasNext()) {
            listenerIterator.next()
                    .computer(ruleKey, target, context, contextHandle) { rk, tg, ct, cth ->
                        computerListener(listenerIterator, rk, tg, ct, cth, methodHandle)
                    }
        } else {
            methodHandle(ruleKey, target, context, contextHandle)
        }
    }
}