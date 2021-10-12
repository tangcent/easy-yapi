package com.itangcent.idea.utils

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.itangcent.common.utils.KV
import com.itangcent.common.utils.asBool
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleComputeListener
import com.itangcent.intellij.config.rule.RuleContext
import com.itangcent.intellij.config.rule.RuleKey
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.jvm.dev.DevEnv
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.jvm.duck.SingleDuckType
import com.itangcent.intellij.jvm.element.ExplicitClass
import com.itangcent.intellij.jvm.element.ExplicitElement
import com.itangcent.intellij.jvm.element.ExplicitMethod
import com.itangcent.intellij.psi.ClassRuleKeys
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
    protected lateinit var configReader: ConfigReader

    @Inject(optional = true)
    private val ruleComputeListener: RuleComputeListener? = null

    @Inject
    private val devEnv: DevEnv? = null

    private val parseContext: ThreadLocal<Deque<String>> = ThreadLocal()
    private val parseScriptContext = ParseScriptContext()

    @PostConstruct
    fun initRuleComputeListener() {
        (ruleComputeListener as? RuleComputeListenerRegistry)?.register(InnerComputeListener())
    }

    override fun beforeParseClass(psiClass: PsiClass, option: Int, kv: KV<String, Any?>) {
        tryInitParseContext()
        ruleComputer.computer(ClassExportRuleKeys.JSON_CLASS_PARSE_BEFORE, psiClass)
        super.beforeParseClass(psiClass, option, kv)
    }

    override fun beforeParseType(psiClass: PsiClass, duckType: SingleDuckType, option: Int, kv: KV<String, Any?>) {
        tryInitParseContext()
        ruleComputer.computer(ClassExportRuleKeys.JSON_CLASS_PARSE_BEFORE, duckType, psiClass)
        super.beforeParseType(psiClass, duckType, option, kv)
    }

    override fun afterParseClass(psiClass: PsiClass, option: Int, kv: KV<String, Any?>) {
        try {
            super.afterParseClass(psiClass, option, kv)
            ruleComputer.computer(ClassExportRuleKeys.JSON_CLASS_PARSE_AFTER, psiClass)
        } finally {
            tryCleanParseContext()
        }
    }

    override fun afterParseType(psiClass: PsiClass, duckType: SingleDuckType, option: Int, kv: KV<String, Any?>) {
        try {
            super.afterParseType(psiClass, duckType, option, kv)
            ruleComputer.computer(ClassExportRuleKeys.JSON_CLASS_PARSE_AFTER, duckType, psiClass)
        } finally {
            tryCleanParseContext()
        }
    }

    protected open fun tryInitParseContext() {
        if (parseContext.get() == null) {
            parseContext.set(LinkedList())
            clearCachePotentially()
        }
    }

    protected open fun tryCleanParseContext() {
        val context = parseContext.get()
        if (context.isNullOrEmpty()) {
            parseContext.remove()
            clearCachePotentially()
        }
    }

    private fun clearCachePotentially() {
        if (configReader.first("json.cache.disable").asBool() == true) {
            devEnv?.dev {
                logger!!.info("clear json cache")
            }
            resolvedInfo.clear()
        }
    }

    override fun beforeParseFieldOrMethod(
        fieldName: String,
        fieldType: DuckType,
        fieldOrMethod: ExplicitElement<*>,
        resourcePsiClass: ExplicitClass,
        option: Int,
        kv: KV<String, Any?>
    ): Boolean {
        pushField(fieldName)
        if (fieldOrMethod is ExplicitMethod) {
            ruleComputer.computer(ClassExportRuleKeys.JSON_METHOD_PARSE_BEFORE, fieldOrMethod)
        } else {
            ruleComputer.computer(ClassExportRuleKeys.JSON_FIELD_PARSE_BEFORE, fieldOrMethod)
        }

        return super.beforeParseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, option, kv)
    }

    private fun pushField(fieldName: String) {
        parseContext.get()?.add(fieldName)
        devEnv?.dev {
            logger!!.info("path -> ${parseScriptContext.path()}")
        }
    }

    override fun onIgnoredParseFieldOrMethod(
        fieldName: String,
        fieldType: DuckType,
        fieldOrMethod: ExplicitElement<*>,
        resourcePsiClass: ExplicitClass,
        option: Int,
        kv: KV<String, Any?>
    ) {
        super.onIgnoredParseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, option, kv)
        popField(fieldName)
    }

    override fun afterParseFieldOrMethod(
        fieldName: String,
        fieldType: DuckType,
        fieldOrMethod: ExplicitElement<*>,
        resourcePsiClass: ExplicitClass,
        option: Int,
        kv: KV<String, Any?>
    ) {
        super.afterParseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, option, kv)

        if (fieldOrMethod is ExplicitMethod) {
            ruleComputer.computer(ClassExportRuleKeys.JSON_METHOD_PARSE_AFTER, fieldOrMethod)
        } else {
            ruleComputer.computer(ClassExportRuleKeys.JSON_FIELD_PARSE_AFTER, fieldOrMethod)
        }
        popField(fieldName)
    }

    private fun popField(fieldName: String) {
        parseContext.get()?.removeLast()
        devEnv?.dev {
            logger!!.info("path -> ${parseScriptContext.path()}")
        }
    }

    inner class ParseScriptContext {
        fun path(): String {
            return parseContext.get()?.joinToString(".") ?: ""
        }

        fun property(property: String): String {
            val context = parseContext.get()
            return if (context.isNullOrEmpty()) {
                property
            } else {
                "${context.joinToString(".")}.$property"
            }
        }
    }

    inner class InnerComputeListener : RuleComputeListener {

        override fun computer(
            ruleKey: RuleKey<*>,
            target: Any,
            context: PsiElement?,
            contextHandle: (RuleContext) -> Unit,
            methodHandle: (RuleKey<*>, Any, PsiElement?, (RuleContext) -> Unit) -> Any?
        ): Any? {
            return if (JSON_RULE_KEYS.contains(ruleKey)) {
                methodHandle(ruleKey, target, context) {
                    contextHandle(it)
                    it.setExt("fieldContext", parseScriptContext)
                }
            } else {
                methodHandle(ruleKey, target, context, contextHandle)
            }
        }
    }


    companion object {
        val JSON_RULE_KEYS = arrayOf(
            ClassRuleKeys.FIELD_IGNORE,
            ClassRuleKeys.FIELD_DOC,
            ClassRuleKeys.FIELD_NAME,
            ClassExportRuleKeys.FIELD_DEFAULT_VALUE,
            ClassExportRuleKeys.JSON_FIELD_PARSE_BEFORE,
            ClassExportRuleKeys.JSON_FIELD_PARSE_AFTER,
            ClassExportRuleKeys.FIELD_REQUIRED
        )
    }
}