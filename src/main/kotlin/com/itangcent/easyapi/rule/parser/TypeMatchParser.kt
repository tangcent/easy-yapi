package com.itangcent.easyapi.rule.parser

import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext

/**
 * Parses type matching expressions in rules to check if a field/method/parameter type matches a specified type.
 *
 * ## Supported Rules
 * This parser handles expressions that look like type names (containing dots but not starting with special prefixes):
 * - `java.lang.String` - matches String type
 * - `java.util.List` - matches List type
 * - `com.example.User` - matches custom types
 *
 * ## Matching Logic
 * Returns `true` if the element's type matches the expression, otherwise returns the original expression.
 * Supports three matching strategies:
 * 1. **Exact match**: `java.lang.String` matches `java.lang.String`
 * 2. **Simple name match**: `String` matches `java.lang.String`
 * 3. **Generic type match**: `java.util.List` matches `java.util.List<String>`
 *
 * ## Supported Elements
 * - **PsiField**: Matches against field type
 * - **PsiMethod**: Matches against return type
 * - **PsiParameter**: Matches against parameter type
 *
 * ## Examples
 * ```
 * # Rule: field.name[java.lang.String]=stringField
 * # Matches fields of type String and renames them to "stringField"
 *
 * # Rule: method.return[java.util.List]=@return list items
 * # Adds documentation to methods returning List
 * ```
 */
class TypeMatchParser : RuleParser {
    override fun canParse(expression: String): Boolean {
        return expression.contains(".") && !expression.startsWith("@") && !expression.startsWith("#") && !expression.startsWith("groovy:")
    }

    override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
        val typeToMatch = expression.trim()
        val elementType = getElementType(context)
        
        if (elementType == null) return expression
        
        return if (typeMatches(elementType, typeToMatch)) true else expression
    }
    
    private fun getElementType(context: RuleContext): String? {
        val element = context.element
        return when (element) {
            is PsiField -> element.type.canonicalText
            is PsiMethod -> element.returnType?.canonicalText
            is PsiParameter -> element.type.canonicalText
            else -> null
        }
    }
    
    private fun typeMatches(actualType: String, expectedType: String): Boolean {
        if (actualType == expectedType) return true
        
        val actualSimpleName = actualType.substringAfterLast('.')
        val expectedSimpleName = expectedType.substringAfterLast('.')
        
        if (actualSimpleName == expectedSimpleName) return true
        
        if (actualType.contains("<")) {
            val rawType = actualType.substringBefore("<")
            if (rawType == expectedType) return true
        }
        
        return false
    }
}
