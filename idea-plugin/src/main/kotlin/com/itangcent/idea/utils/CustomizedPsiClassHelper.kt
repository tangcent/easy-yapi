package com.itangcent.idea.utils

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.itangcent.common.constant.Attrs
import com.itangcent.common.utils.KV
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.intellij.jvm.SingleDuckType
import com.itangcent.intellij.psi.DefaultPsiClassHelper

/**
 * 1.support rule:["field.required"]
 */
class CustomizedPsiClassHelper : DefaultPsiClassHelper() {

    @Suppress("UNCHECKED_CAST")
    override fun afterParseFieldOrMethod(fieldName: String, fieldType: PsiType, fieldOrMethod: PsiElement, resourcePsiClass: PsiClass, duckType: SingleDuckType, option: Int, kv: KV<String, Any?>) {
        super.afterParseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, duckType, option, kv)

        ruleComputer!!.computer(ClassExportRuleKeys.FIELD_REQUIRED, fieldOrMethod)?.let { required ->
            var requiredKV: KV<String, Any?>? = kv[Attrs.REQUIRED_ATTR] as KV<String, Any?>?
            if (requiredKV == null) {
                requiredKV = KV.create()
                kv[Attrs.REQUIRED_ATTR] = requiredKV
            }
            requiredKV[fieldName] = required
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun afterParseFieldOrMethod(fieldName: String, fieldType: PsiType, fieldOrMethod: PsiElement, resourcePsiClass: PsiClass, option: Int, kv: KV<String, Any?>) {
        super.afterParseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, option, kv)

        ruleComputer!!.computer(ClassExportRuleKeys.FIELD_REQUIRED, fieldOrMethod)?.let { required ->
            var requiredKV: KV<String, Any?>? = kv[Attrs.REQUIRED_ATTR] as KV<String, Any?>?
            if (requiredKV == null) {
                requiredKV = KV.create()
                kv[Attrs.REQUIRED_ATTR] = requiredKV
            }
            requiredKV[fieldName] = required
        }
    }

}