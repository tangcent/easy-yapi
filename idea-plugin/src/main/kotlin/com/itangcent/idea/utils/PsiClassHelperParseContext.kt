package com.itangcent.idea.utils

import com.google.inject.Inject
import com.itangcent.intellij.config.ConfigReader
import java.util.*

class PsiClassHelperParseContext {

    @Inject
    private val configReader: ConfigReader? = null

    private val parseContext: ThreadLocal<Deque<String>> = ThreadLocal()

    private val scriptSupport = ScriptSupport()

    fun tryCleanContext() {
        val context = parseContext.get()
        if (context.isNullOrEmpty()) {
            parseContext.remove()
        }
    }

    fun initContext() {
        parseContext.set(LinkedList())
    }

    fun hasContext(): Boolean {
        return parseContext.get() != null
    }

    inner class ScriptSupport {
        fun path(): String {
            return parseContext.get()?.joinToString(".") ?: ""
        }
    }
}