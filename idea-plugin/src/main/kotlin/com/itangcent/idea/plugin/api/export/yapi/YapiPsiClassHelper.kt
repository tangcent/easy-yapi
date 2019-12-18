package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.itangcent.common.constant.Attrs
import com.itangcent.common.utils.KV
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.idea.utils.CustomizedPsiClassHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.extend.toBoolean
import com.itangcent.intellij.jvm.SingleDuckType
import com.itangcent.intellij.psi.ContextSwitchListener

/**
 * 1.support rule:["field.mock"]
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
    override fun afterParseFieldOrMethod(fieldName: String, fieldType: PsiType, fieldOrMethod: PsiElement, resourcePsiClass: PsiClass, duckType: SingleDuckType, option: Int, kv: KV<String, Any?>) {
        super.afterParseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, duckType, option, kv)

        ruleComputer!!.computer(ClassExportRuleKeys.FIELD_MOCK, fieldOrMethod)
                ?.takeIf { !it.isBlank() }
                ?.let { if (resolveProperty) configReader!!.resolveProperty(it) else it }
                ?.let { mockInfo ->
                    var mockKV: KV<String, Any?>? = kv[Attrs.MOCK_ATTR] as KV<String, Any?>?
                    if (mockKV == null) {
                        mockKV = KV.create()
                        kv[Attrs.MOCK_ATTR] = mockKV
                    }
                    mockKV[fieldName] = mockInfo
                }
    }

    @Suppress("UNCHECKED_CAST")
    override fun afterParseFieldOrMethod(fieldName: String, fieldType: PsiType, fieldOrMethod: PsiElement, resourcePsiClass: PsiClass, option: Int, kv: KV<String, Any?>) {
        super.afterParseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, option, kv)

        ruleComputer!!.computer(ClassExportRuleKeys.FIELD_MOCK, fieldOrMethod)
                ?.takeIf { !it.isBlank() }
                ?.let { if (resolveProperty) configReader!!.resolveProperty(it) else it }
                ?.let { required ->
                    var mockKV: KV<String, Any?>? = kv[Attrs.MOCK_ATTR] as KV<String, Any?>?
                    if (mockKV == null) {
                        mockKV = KV.create()
                        kv[Attrs.MOCK_ATTR] = mockKV
                    }
                    mockKV[fieldName] = required
                }
    }

}