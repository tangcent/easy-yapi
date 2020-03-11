package com.itangcent.idea.utils

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.itangcent.common.constant.Attrs
import com.itangcent.common.utils.KV
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.psi.DefaultPsiClassHelper
import com.itangcent.intellij.util.sub

/**
 * 1.support rule:["field.required"]
 */
open class CustomizedPsiClassHelper : DefaultPsiClassHelper() {

    override fun afterParseFieldOrMethod(fieldName: String, fieldType: DuckType, fieldOrMethod: PsiElement, resourcePsiClass: PsiClass, option: Int, kv: KV<String, Any?>) {
        super.afterParseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, option, kv)

        ruleComputer!!.computer(ClassExportRuleKeys.FIELD_REQUIRED, fieldOrMethod)?.let { required ->
            kv.sub(Attrs.REQUIRED_ATTR)[fieldName] = required
        }
    }

}