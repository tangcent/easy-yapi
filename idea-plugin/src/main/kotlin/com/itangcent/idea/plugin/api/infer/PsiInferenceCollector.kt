package com.itangcent.idea.plugin.api.infer

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiMethod
import com.itangcent.common.logger.traceError
import com.itangcent.intellij.context.ActionContext
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
    private lateinit var actionContext: ActionContext

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
                val psiClass = psiResolver.resolveClass(className, methodContext)

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
                    logger.warn("Could not find class: $className")
                }
            } catch (e: Exception) {
                logger.traceError("Error collecting information for class: $className", e)
            }
        }

        return result
    }

    /**
     * Creates a formatted string representation of class information for inclusion in AI prompts
     */
    fun formatClassInfoForPrompt(classInfoList: List<AIMethodInferHelper.ClassInfo>): String {
        if (classInfoList.isEmpty()) {
            return ""
        }

        return """
            Additional Classes:
            ${
            classInfoList.joinToString("\n\n") { classInfo ->
                """
                Class: ${classInfo.name}
                Fields:
                ${classInfo.fields.joinToString("\n") { "- ${it.name}: ${it.type} ${it.annotations.joinToString(" ") { "@$it" }}" }}
                
                Methods:
                ${
                    classInfo.methods.joinToString("\n") { method ->
                        "- ${method.name}(${method.parameters.joinToString(", ")}): ${method.returnType}"
                    }
                }
                """.trimIndent()
            }
        }
        """.trimIndent()
    }
} 