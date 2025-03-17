package com.itangcent.idea.plugin.api.infer

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiMethod
import com.itangcent.common.logger.traceError
import com.itangcent.intellij.jvm.PsiResolver
import com.itangcent.intellij.logger.Logger

/**
 * Utility class for handling AI inference-related operations
 */
@Singleton
class PsiInferenceCollector {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var psiResolver: PsiResolver

    /**
     * Collects information about additional classes requested by the AI
     *
     * @param classNames List of fully qualified class names to collect information about
     * @param methodContext The method context for resolving classes
     * @return A list of ClassInfo objects containing information about the requested classes
     */
    fun collectAdditionalClassInfo(
        classNames: List<String>,
        methodContext: PsiMethod
    ): List<AIMethodInferHelper.ClassInfo> {
        val result = mutableListOf<AIMethodInferHelper.ClassInfo>()

        for (className in classNames) {
            try {
                // Find the class by name
                var psiClass = psiResolver.resolveClass(className, methodContext)

                // If the class wasn't found with the fully qualified name, try with just the simple name
                if (psiClass == null) {
                    // Extract the simple class name (everything after the last dot)
                    val simpleClassName = className.substringAfterLast('.')
                    if (simpleClassName.isNotEmpty() && simpleClassName != className) {
                        logger.trace("Could not find class with fully qualified name: $className. Trying with simple name: $simpleClassName")
                        psiClass = psiResolver.resolveClass(simpleClassName, methodContext)
                    }
                }

                if (psiClass != null) {
                    // Create ClassInfo for the found class
                    val classInfo = AIMethodInferHelper.ClassInfo(
                        name = psiClass.qualifiedName ?: psiClass.name ?: className,
                        fields = psiClass.fields.map { field ->
                            AIMethodInferHelper.FieldInfo(
                                name = field.name,
                                type = field.type.presentableText,
                                annotations = field.annotations.map { it.qualifiedName ?: it.text }
                            )
                        },
                        methods = psiClass.methods
                            .filter { it.name != "equals" && it.name != "hashCode" && it.name != "toString" }
                            .take(10) // Limit the number of methods to include
                            .map { method ->
                                AIMethodInferHelper.MethodSummary(
                                    name = method.name,
                                    returnType = method.returnType?.presentableText ?: "void",
                                    parameters = method.parameterList.parameters.map {
                                        "${it.name}: ${it.type.presentableText}"
                                    }
                                )
                            }
                    )

                    result.add(classInfo)
                } else {
                    logger.trace("Could not find class: $className")
                }
            } catch (e: Exception) {
                logger.traceError("Error collecting information for class: $className", e)
            }
        }

        return result
    }
} 