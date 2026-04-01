package com.itangcent.easyapi.ide.script

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

/**
 * Interface representing a context for script execution.
 * Provides the target element and display name for the script executor.
 */
interface ScriptContext {
    /** Returns the target element for script execution */
    fun element(): Any?
    
    /** Returns a human-readable name for this context */
    fun name(): String
}

/**
 * Simple implementation of ScriptContext.
 * Wraps any object with an optional display name.
 * 
 * @property target The target object for script execution
 * @property displayName Optional display name, auto-generated if null
 */
class SimpleScriptContext(
    private val target: Any,
    private val displayName: String? = null
) : ScriptContext {
    override fun element(): Any = target

    override fun name(): String = displayName ?: when (target) {
        is PsiClass -> target.qualifiedName ?: target.name ?: "unknown"
        is PsiElement -> target.text
        else -> target.toString()
    }

    override fun toString(): String = name()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SimpleScriptContext) return false
        return target == other.target
    }

    override fun hashCode(): Int = target.hashCode()
}

/** Empty context placeholder for initial state */
val EMPTY_SCRIPT_CONTEXT = SimpleScriptContext(Any(), "<select class>")

/**
 * Holds script execution information.
 * 
 * @property script The script content to execute
 * @property scriptType The script language support
 * @property context The execution context (target element)
 * @property scriptUpdateTime Timestamp of last script modification
 */
data class ScriptInfo(
    val script: String,
    val scriptType: ScriptSupport?,
    val context: Any?,
    var scriptUpdateTime: Long = System.currentTimeMillis()
)
