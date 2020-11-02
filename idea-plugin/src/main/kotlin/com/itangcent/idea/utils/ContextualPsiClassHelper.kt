package com.itangcent.idea.utils

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.utils.KV
import com.itangcent.common.utils.asBool
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.jvm.duck.SingleDuckType
import com.itangcent.intellij.jvm.element.ExplicitClass
import com.itangcent.intellij.jvm.element.ExplicitElement
import com.itangcent.intellij.psi.DefaultPsiClassHelper
import java.util.*

/**
 * support config:
 * 1. json.cache.disable
 * support rules:
 * 1. field.parse.before
 * 2. field.parse.after
 */
open class ContextualPsiClassHelper : DefaultPsiClassHelper() {

    @Inject
    private val configReader: ConfigReader? = null

    private val parseContext: ThreadLocal<Deque<String>> = ThreadLocal()
    private val parseScriptContext = ParseScriptContext()

    override fun beforeParseClass(psiClass: PsiClass, option: Int, kv: KV<String, Any?>) {
        tryInitParseContext()
        super.beforeParseClass(psiClass, option, kv)
    }

    override fun beforeParseType(psiClass: PsiClass, duckType: SingleDuckType, option: Int, kv: KV<String, Any?>) {
        tryInitParseContext()
        super.beforeParseType(psiClass, duckType, option, kv)
    }

    override fun afterParseClass(psiClass: PsiClass, option: Int, kv: KV<String, Any?>) {
        try {
            super.afterParseClass(psiClass, option, kv)
        } finally {
            tryCleanParseContext()
        }
    }

    override fun afterParseType(psiClass: PsiClass, duckType: SingleDuckType, option: Int, kv: KV<String, Any?>) {
        try {
            super.afterParseType(psiClass, duckType, option, kv)
        } finally {
            tryCleanParseContext()
        }
    }

    protected open fun tryInitParseContext() {
        if (parseContext.get() == null) {
            initParseContext()
        }
    }

    protected open fun tryCleanParseContext() {
        val context = parseContext.get()
        if (context.isNullOrEmpty()) {
            parseContext.remove()
        }
    }

    protected open fun initParseContext() {
        if (configReader!!.first("json.cache.disable").asBool() == true) {
            resolvedInfo.clear()
        }
        parseContext.set(LinkedList())
    }

    override fun parseFieldOrMethod(fieldName: String, fieldType: DuckType, fieldOrMethod: ExplicitElement<*>, resourcePsiClass: ExplicitClass, option: Int, kv: KV<String, Any?>) {
        parseContext.get().add(fieldName)
        ruleComputer!!.computer(ClassExportRuleKeys.FIELD_PARSE_BEFORE, fieldOrMethod, fieldOrMethod.psi()) {
            it.setExt("context", parseScriptContext)
        }
        super.parseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, option, kv)
    }

    override fun afterParseFieldOrMethod(fieldName: String, fieldType: DuckType, fieldOrMethod: ExplicitElement<*>, resourcePsiClass: ExplicitClass, option: Int, kv: KV<String, Any?>) {
        super.afterParseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, option, kv)
        ruleComputer!!.computer(ClassExportRuleKeys.FIELD_PARSE_AFTER, fieldOrMethod, fieldOrMethod.psi()) {
            it.setExt("context", parseScriptContext)
        }
        parseContext.get().pop()
    }

    inner class ParseScriptContext {

        fun path(): String {
            return parseContext.get()?.joinToString(".") ?: ""
        }

    }
}