package com.itangcent.easyapi.rule.parser

import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext

/**
 * Parses `$class:` expressions for class type matching.
 *
 * Supports:
 * - `$class:com.example.Foo` — exact class match
 * - `$class:? extend com.example.Foo` — checks if the element's class extends/implements Foo
 *
 * ## Usage
 * ```
 * # Match exact class
 * $class:com.example.User
 *
 * # Match any class extending BaseEntity
 * $class:? extend com.example.BaseEntity
 *
 * # Match any class implementing Serializable
 * $class:? extend java.io.Serializable
 * ```
 *
 * ## Element Resolution
 * The parser resolves the class from different PSI element types:
 * - `PsiClass` - Returns the class directly
 * - `PsiField` - Resolves from field type
 * - `PsiMethod` - Resolves from return type
 * - `PsiParameter` - Resolves from parameter type
 *
 * @see RuleParser for the interface
 */
class ClassMatchParser : RuleParser {

    override fun canParse(expression: String): Boolean =
        expression.trim().startsWith("\$class:")

    override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any {
        val element = context.element
        val psiType = context.psiType
        if (element == null && psiType == null) return false
        val content = expression.trim().removePrefix("\$class:").trim()
        if (content.isEmpty()) return false

        return if (content.startsWith("? extend")) {
            val target = content.removePrefix("? extend").trim()
            checkExtends(element, psiType, target)
        } else {
            checkExact(element, psiType, content)
        }
    }

    private fun checkExact(element: PsiElement?, psiType: com.intellij.psi.PsiType?, className: String): Boolean {
        val psiClass = resolveClass(element, psiType) ?: return false
        return psiClass.qualifiedName == className
    }

    private fun checkExtends(element: PsiElement?, psiType: com.intellij.psi.PsiType?, target: String): Boolean {
        val psiClass = resolveClass(element, psiType) ?: return false
        return extendsOrImplements(psiClass, target)
    }

    private fun extendsOrImplements(psiClass: PsiClass, target: String): Boolean {
        var cls: PsiClass? = psiClass
        while (cls != null) {
            if (cls.qualifiedName == target) return true
            for (iface in cls.interfaces) {
                if (iface.qualifiedName == target) return true
            }
            cls = cls.superClass
        }
        return false
    }

    companion object {
        fun resolveClass(element: PsiElement?, psiType: com.intellij.psi.PsiType? = null): PsiClass? {
            // Prefer psiType if available
            if (psiType != null) {
                return PsiTypesUtil.getPsiClass(psiType)
            }
            return when (element) {
                is PsiClass -> element
                is PsiField -> PsiTypesUtil.getPsiClass(element.type)
                is PsiMethod -> element.returnType?.let { PsiTypesUtil.getPsiClass(it) }
                is PsiParameter -> PsiTypesUtil.getPsiClass(element.type)
                else -> null
            }
        }
    }
}
