package com.itangcent.idea.utils

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.itangcent.common.constant.Attrs
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.intellij.psi.DefaultPsiClassHelper
import com.itangcent.intellij.psi.SingleDuckType
import com.itangcent.intellij.util.KV

/**
 *
 * 1.resolve HttpEntity/RequestEntity/ResponseEntity
 * 2.support rule:["field.required"]
 */
class CustomizedPsiClassHelper : DefaultPsiClassHelper() {

    override fun getTypeObject(clsWithParam: SingleDuckType?, context: PsiElement, option: Int): Any? {
        if (clsWithParam != null) {
            val psiClassName = clsWithParam.psiClass().qualifiedName
            if (psiClassName == HTTP_ENTITY
                    || psiClassName == RESPONSE_ENTITY
                    || psiClassName == REQUEST_ENTITY) {
                if (clsWithParam.genericInfo == null) {
                    return Any()
                }
                var entityType = clsWithParam.genericInfo!!["T"]
                if (entityType == null && clsWithParam.genericInfo!!.size == 1) {
                    entityType = clsWithParam.genericInfo!!.values.first()
                }
                if (entityType == null) {
                    return Any()
                }
                return super.getTypeObject(entityType, context, option)
            }
        }

        return super.getTypeObject(clsWithParam, context, option)
    }

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

    companion object {
        const val HTTP_ENTITY = "org.springframework.http.HttpEntity"

        const val REQUEST_ENTITY = "org.springframework.http.RequestEntity"

        const val RESPONSE_ENTITY = "org.springframework.http.ResponseEntity"
    }
}