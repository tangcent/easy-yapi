package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.itangcent.common.constant.Attrs
import com.itangcent.common.utils.sub
import com.itangcent.common.utils.KV
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.idea.utils.CustomizedPsiClassHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.jvm.element.ExplicitClass
import com.itangcent.intellij.jvm.element.ExplicitElement
import com.itangcent.intellij.psi.ContextSwitchListener

/**
 * support rules:
 * 1. field.mock
 * 2. field.demo
 */
class YapiPsiClassHelper : CustomizedPsiClassHelper() {

    @Inject(optional = true)
    private val configReader: ConfigReader? = null

    private var resolveProperty: Boolean = true

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

    override fun afterParseFieldOrMethod(fieldName: String, fieldType: DuckType, fieldOrMethod: ExplicitElement<*>, resourcePsiClass: ExplicitClass, option: Int, kv: KV<String, Any?>) {
        //compute `field.mock`
        ruleComputer!!.computer(ClassExportRuleKeys.FIELD_MOCK, fieldOrMethod)
                ?.takeIf { !it.isBlank() }
                ?.let { if (resolveProperty) configReader!!.resolveProperty(it) else it }
                ?.let { mockInfo ->
                    kv.sub(Attrs.MOCK_ATTR)[fieldName] = mockInfo
                }

        //compute `field.demo`
        val demoValue = ruleComputer.computer(YapiClassExportRuleKeys.FIELD_DEMO,
                fieldOrMethod)
        if (demoValue.notNullOrBlank()) {
            kv.sub(Attrs.DEMO_ATTR)[fieldName] = demoValue
        }

        super.afterParseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, option, kv)
    }

}