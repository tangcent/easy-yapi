package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.itangcent.common.constant.Attrs
import com.itangcent.common.utils.KV
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.idea.utils.CustomizedPsiClassHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.extend.toBoolean
import com.itangcent.intellij.extend.toPrettyString
import com.itangcent.intellij.jvm.PsiUtils
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.intellij.util.sub

/**
 * 1.support rule:["field.mock"]
 * 2.support rule:["field.default.value"]
 */
class YapiPsiClassHelper : CustomizedPsiClassHelper() {

    @Inject(optional = true)
    val configReader: ConfigReader? = null

    var resolveProperty: Boolean = true

    @PostConstruct
    fun initYapiInfo() {
        val contextSwitchListener: ContextSwitchListener? = ActionContext.getContext()
                ?.instance(ContextSwitchListener::class)
        contextSwitchListener!!.onModuleChange {
            val resolveProperty = configReader!!.first("field.mock.resolveProperty")
            if (!resolveProperty.isNullOrBlank()) {
                this.resolveProperty = resolveProperty.toBoolean() ?: true
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun afterParseFieldOrMethod(fieldName: String, fieldType: DuckType, fieldOrMethod: PsiElement, resourcePsiClass: PsiClass, option: Int, kv: KV<String, Any?>) {
        super.afterParseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, option, kv)

        ruleComputer!!.computer(ClassExportRuleKeys.FIELD_MOCK, fieldOrMethod)
                ?.takeIf { !it.isBlank() }
                ?.let { if (resolveProperty) configReader!!.resolveProperty(it) else it }
                ?.let { mockInfo ->
                    kv.sub(Attrs.MOCK_ATTR)[fieldName] = mockInfo
                }

        val defaultValue = ruleComputer.computer(ClassExportRuleKeys.FIELD_DEFAULT_VALUE, fieldOrMethod)
        if (defaultValue.isNullOrEmpty()) {
            if (fieldOrMethod is PsiField) {
                fieldOrMethod.initializer?.let { PsiUtils.resolveExpr(it) }?.toPrettyString()
                        ?.let { kv.sub(Attrs.DEFAULT_VALUE_ATTR)[fieldName] = it }
            }
        } else {
            kv.sub(Attrs.DEFAULT_VALUE_ATTR)[fieldName] = defaultValue
        }
    }

}