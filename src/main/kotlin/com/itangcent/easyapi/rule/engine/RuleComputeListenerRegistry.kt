package com.itangcent.easyapi.rule.engine

import com.intellij.psi.PsiElement
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registry for rule computation listeners.
 *
 * Thread-safe registry that manages [RuleComputeListener] instances.
 * Uses CopyOnWriteArrayList for safe concurrent access.
 *
 * ## Usage
 * ```kotlin
 * val registry = RuleComputeListenerRegistry()
 *
 * // Register a listener
 * registry.register(myListener)
 *
 * // Notify all listeners
 * registry.notify("api.name", psiElement, "getUser")
 * ```
 *
 * @see RuleComputeListener for the listener interface
 */
class RuleComputeListenerRegistry {
    private val listeners = CopyOnWriteArrayList<RuleComputeListener>()

    /**
     * Registers a listener to receive rule computation events.
     *
     * @param listener The listener to register
     */
    fun register(listener: RuleComputeListener) {
        listeners.add(listener)
    }

    /**
     * Notifies all registered listeners of a rule computation.
     *
     * @param key The rule key
     * @param element The PSI element context
     * @param result The evaluation result
     */
    fun notify(key: String, element: PsiElement?, result: Any?) {
        for (listener in listeners) {
            listener.onRuleComputed(key, element, result)
        }
    }
}
