package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.intellij.jvm.PsiResolver
import java.util.concurrent.ConcurrentHashMap

/*
 * This class provides a custom implementation for resolving whether a given PsiClass
 * has a Spring controller annotation. It supports meta-annotations, allowing for
 * custom annotations that are themselves annotated with Spring controller annotations.
 * For example, a custom annotation that is annotated with @Controller will be recognized
 * ```java
 * @Target({ElementType.TYPE})
 * @Retention(RetentionPolicy.RUNTIME)
 * @Documented
 * @Controller
 * public @interface XxxxController {
 *     @AliasFor(
 *         annotation = Controller.class
 *     )
 *     String value() default "";
 * }
 * ```
 */
class CustomSpringControllerAnnotationResolver : SpringControllerAnnotationResolver {

    @Inject
    private lateinit var psiResolver: PsiResolver

    @Inject
    private lateinit var standardSpringControllerAnnotationResolver: StandardSpringControllerAnnotationResolver

    /**
     * A cache to store the resolution results of whether a given annotation is a Spring controller annotation.
     * The key is the qualified name of the annotation, and the value is a boolean indicating if it is a controller annotation.
     */
    private val controllerAnnotationLookup = ConcurrentHashMap<String, Boolean>()

    override fun hasControllerAnnotation(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            val annotationQualifiedName = annotation.qualifiedName ?: return@any false
            controllerAnnotationLookup.computeIfAbsent(annotationQualifiedName) {
                val annotationClass = annotation.resolveAnnotationType()
                    ?: psiResolver.resolveClass(annotationQualifiedName, psiClass)
                    ?: return@computeIfAbsent false
                return@computeIfAbsent standardSpringControllerAnnotationResolver.hasControllerAnnotation(
                    annotationClass
                )
            }
        }
    }
} 