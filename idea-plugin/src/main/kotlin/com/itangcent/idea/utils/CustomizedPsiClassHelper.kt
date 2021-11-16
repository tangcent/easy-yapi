package com.itangcent.idea.utils

import com.google.inject.Inject
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.itangcent.common.constant.Attrs
import com.itangcent.common.utils.*
import com.itangcent.idea.plugin.api.export.AdditionalField
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.extend.toPrettyString
import com.itangcent.intellij.jvm.PsiExpressionResolver
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.jvm.element.ExplicitClass
import com.itangcent.intellij.jvm.element.ExplicitElement
import com.itangcent.intellij.jvm.element.ExplicitField
import com.itangcent.intellij.psi.unwrapped

/**
 * support rules:
 * 1. field.required
 * 2. field.default.value
 */
open class CustomizedPsiClassHelper : ContextualPsiClassHelper() {

    @Inject
    private lateinit var psiExpressionResolver: PsiExpressionResolver

    override fun afterParseFieldOrMethod(
        fieldName: String,
        fieldType: DuckType,
        fieldOrMethod: ExplicitElement<*>,
        resourcePsiClass: ExplicitClass,
        option: Int,
        kv: KV<String, Any?>
    ) {
        //compute `field.required`
        ruleComputer.computer(ClassExportRuleKeys.FIELD_REQUIRED, fieldOrMethod)?.let { required ->
            kv.sub(Attrs.REQUIRED_ATTR)[fieldName] = required
        }

        //compute `field.default.value`
        val defaultValue = ruleComputer.computer(ClassExportRuleKeys.FIELD_DEFAULT_VALUE, fieldOrMethod)
        if (defaultValue.isNullOrEmpty()) {
            if (fieldOrMethod is ExplicitField) {
                fieldOrMethod.psi().initializer?.let { psiExpressionResolver.process(it) }?.toPrettyString()
                    ?.let { kv.sub(Attrs.DEFAULT_VALUE_ATTR)[fieldName] = it }
            }
        } else {
            kv.sub(Attrs.DEFAULT_VALUE_ATTR)[fieldName] = defaultValue
            populateFieldValue(fieldName, fieldType, kv, defaultValue)
        }

        super.afterParseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, option, kv)
    }

    override fun resolveAdditionalField(
        additionalField: AdditionalField,
        context: PsiElement,
        option: Int,
        kv: KV<String, Any?>
    ) {
        super.resolveAdditionalField(additionalField, context, option, kv)
        val fieldName = additionalField.name!!
        kv.sub(Attrs.REQUIRED_ATTR)[fieldName] = additionalField.required
        kv.sub(Attrs.DEFAULT_VALUE_ATTR)[fieldName] = additionalField.defaultValue
    }

    protected fun populateFieldValue(fieldName: String, fieldType: DuckType, kv: KV<String, Any?>, valueText: String) {
        val obj = try {
            GsonExUtils.fromJson<Any>(valueText)
        } catch (e: Exception) {
            LOG.error("failed parse json:\n$valueText\n", e)
            return
        }
        if (isOriginal(obj)) {
            return
        }
        val oldValue = kv[fieldName].unwrapped()
        if (isOriginal(oldValue)) {
            kv[fieldName] = obj
        } else {
            kv[fieldName] = oldValue.copy()
            kv.merge(fieldName, obj)
        }
    }

    /**
     * check if the object is original
     * like:
     * default primary: 0, 0.0
     * default blank string: ""
     * array with original: [0],[0.0],[""]
     * list with original: [0],[0.0],[""]
     * map with original: {"key":0}
     */
    private fun isOriginal(obj: Any?): Boolean {
        when (obj) {
            null -> {
                return true
            }
            is Array<*> -> {
                return obj.size == 0 || (obj.size == 1 && isOriginal(obj[0]))
            }
            is Collection<*> -> {
                return obj.size == 0 || (obj.size == 1 && isOriginal(obj.first()))
            }
            is Map<*, *> -> {
                return obj.size == 0 || (obj.size == 1 && obj.entries.first().let {
                    (it.key == "key" || isOriginal(it.key)) && isOriginal(it.value)
                })
            }
            is Boolean -> {
                return obj
            }
            is Number -> {
                return obj.toDouble() == 0.0
            }
            is String -> {
                return obj.isBlank()
            }
            else -> return false
        }
    }

    override fun ignoreField(psiField: PsiField): Boolean {
        if (configReader.first("ignore_static_and_final")?.asBool() == false) {
            return false
        }
        return super.ignoreField(psiField)
    }
}

private val LOG = org.apache.log4j.Logger.getLogger(CustomizedPsiClassHelper::class.java)