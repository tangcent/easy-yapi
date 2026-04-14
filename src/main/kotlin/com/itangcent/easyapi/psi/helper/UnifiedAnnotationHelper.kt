package com.itangcent.easyapi.psi.helper

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiReferenceExpression
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.psi.adapter.GroovyPsiAdapter
import com.itangcent.easyapi.psi.adapter.JavaPsiAdapter
import com.itangcent.easyapi.psi.adapter.KotlinPsiAdapter
import com.itangcent.easyapi.psi.adapter.PsiLanguageAdapter
import com.itangcent.easyapi.psi.adapter.ScalaPsiAdapter
import kotlinx.coroutines.withContext

/**
 * Unified implementation of [AnnotationHelper] that supports multiple languages.
 *
 * Uses language-specific adapters to handle annotations from:
 * - Java (standard PSI)
 * - Kotlin (Kotlin PSI)
 * - Groovy (Groovy PSI)
 * - Scala (Scala PSI)
 *
 * ## Value Normalization
 * Converts annotation values to standard Kotlin types:
 * - Literals → String, Number, Boolean
 * - Class references → canonical class name
 * - Arrays → List<Any?>
 * - Enum references → enum constant name
 * - Constant expressions → evaluated value (e.g., `A + "/" + B`)
 *
 * @see AnnotationHelper for the interface
 * @see PsiLanguageAdapter for language-specific handling
 */
class UnifiedAnnotationHelper : AnnotationHelper {

    private val adapters: List<PsiLanguageAdapter> = listOf(
        JavaPsiAdapter(),
        KotlinPsiAdapter(),
        ScalaPsiAdapter(),
        GroovyPsiAdapter()
    )

    override suspend fun hasAnn(element: PsiElement, annFqn: String): Boolean {
        return read {
            findPsiAnnotations(element, annFqn).isNotEmpty()
        }
    }

    override suspend fun findAnnMap(element: PsiElement, annFqn: String): Map<String, Any?>? {
        return read {
            findPsiAnnotations(element, annFqn).firstOrNull()?.let { normalizeAttributes(it, element.project) }
        }
    }

    override suspend fun findAnnMaps(element: PsiElement, annFqn: String): List<Map<String, Any?>>? {
        return read {
            val anns = findPsiAnnotations(element, annFqn)
            if (anns.isEmpty()) return@read null
            anns.map { normalizeAttributes(it, element.project) }
        }
    }

    override suspend fun findAttr(element: PsiElement, annFqn: String, attr: String): Any? {
        return read {
            val ann = findPsiAnnotations(element, annFqn).firstOrNull() ?: return@read null
            val value = ann.findAttributeValue(attr) ?: return@read null
            normalizeValue(value, element.project)
        }
    }

    override suspend fun findAttrAsString(element: PsiElement, annFqn: String, attr: String): String? {
        val v = findAttr(element, annFqn, attr) ?: return null
        return when (v) {
            is String -> v
            else -> v.toString()
        }
    }

    private fun findPsiAnnotations(element: PsiElement, annFqn: String): List<PsiAnnotation> {
        val adapter = adapters.firstOrNull { it.supportsElement(element) } ?: return emptyList()
        return adapter.resolveAnnotations(element).filter { it.qualifiedName == annFqn }
    }

    private fun normalizeAttributes(ann: PsiAnnotation, project: Project): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        val attrs = ann.parameterList.attributes
        for (attr in attrs) {
            val name = attr.name ?: "value"
            val value = attr.value ?: continue
            result[name] = normalizeValue(value, project)
        }
        return result
    }

    private fun normalizeValue(value: PsiAnnotationMemberValue, project: Project): Any? {
        return when (value) {
            is PsiLiteralExpression -> value.value
            is PsiClassObjectAccessExpression -> value.operand.type.canonicalText
            is PsiArrayInitializerMemberValue -> value.initializers.mapNotNull { normalizeValue(it, project) }
            is PsiReferenceExpression -> {
                val resolved = value.resolve()
                when (resolved) {
                    is PsiEnumConstant -> resolved.name
                    else -> evaluateConstantExpression(value, project) ?: value.text
                }
            }

            is PsiExpression -> evaluateConstantExpression(value, project) ?: value.text.trim('"')
            else -> value.text.trim('"')
        }
    }

    private fun evaluateConstantExpression(expression: PsiExpression, project: Project): Any? {
        return try {
            JavaPsiFacade.getInstance(project)
                .constantEvaluationHelper
                .computeConstantExpression(expression)
        } catch (_: Exception) {
            null
        }
    }
}