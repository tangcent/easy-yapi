package com.itangcent.idea.utils

import com.itangcent.common.constant.Attrs
import com.itangcent.common.utils.KV
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.jvm.element.ExplicitClass
import com.itangcent.intellij.jvm.element.ExplicitElement
import com.itangcent.intellij.psi.DefaultPsiClassHelper
import com.itangcent.intellij.util.sub

/**
 * 1.support rule:["field.required"]
 */
class CustomizedPsiClassHelper : DefaultPsiClassHelper() {

    override fun afterParseFieldOrMethod(fieldName: String, fieldType: DuckType, fieldOrMethod: ExplicitElement<*>, resourcePsiClass: ExplicitClass, option: Int, kv: KV<String, Any?>) {
        super.afterParseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, option, kv)
        ruleComputer!!.computer(ClassExportRuleKeys.FIELD_REQUIRED, fieldOrMethod)?.let { required ->
            kv.sub(Attrs.REQUIRED_ATTR)[fieldName] = required
        }
    }

}