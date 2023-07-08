package com.itangcent.condition

import com.itangcent.intellij.context.ActionContext
import com.itangcent.utils.superClasses
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass


/**
 * Condition that checks for the special annotation [T].
 */
abstract class AnnotatedCondition<T : Annotation> : Condition, ConditionSupported {

    private val supportedConditionClass by lazy {
        annClass()
    }

    private val annotationCache = ConcurrentHashMap<KClass<*>, Array<T>>()

    override fun matches(actionContext: ActionContext, beanClass: KClass<*>): Boolean {
        return getAnnotations(beanClass)
            .all { matches(actionContext, it) }
    }

    override fun supported(beanClass: KClass<*>): Boolean {
        return getAnnotations(beanClass).isNotEmpty()
    }

    private fun getAnnotations(beanClass: KClass<*>): Array<T> {
        return annotationCache.computeIfAbsent(beanClass) {
            collectAnnotations(it)
        }
    }

    /**
     * Collect all annotations of the specified type for a given bean class and its superclasses,
     * using reflection to access the annotations.
     */
    @Suppress("UNCHECKED_CAST")
    private fun collectAnnotations(beanClass: KClass<*>): Array<T> {
        val annotations = ArrayList<T>()
        beanClass.superClasses { kClass ->
            kClass.annotations.filter { supportedConditionClass.isInstance(it) }
                .forEach {
                    annotations.add(it as T)
                }
        }
        val array = java.lang.reflect.Array.newInstance(supportedConditionClass.java, annotations.size) as Array<T>
        return annotations.toArray(array)
    }

    /**
     * Returns the annotation class for the given type parameter T by inspecting the class hierarchy of the implementing class.
     */
    protected open fun annClass(): KClass<T> {
        return findAnnClass(this::class)
            ?: throw IllegalArgumentException("failed get condition class of ${this::class}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun findAnnClass(cls: KClass<*>): KClass<T>? {
        for (supertype in cls.supertypes) {
            val classifier = supertype.classifier
            if (classifier == AnnotatedCondition::class) {
                return supertype.arguments[0].type!!.classifier as KClass<T>
            }
            if (classifier is KClass<*>) {
                findAnnClass(classifier)?.let {
                    return it
                }
            }
        }
        return null
    }

    /**
     *  Abstract function that must be implemented by subclasses to check whether a given ActionContext matches a specific annotation.
     */
    protected abstract fun matches(actionContext: ActionContext, annotation: T): Boolean
}
