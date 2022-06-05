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

    @Suppress("UNCHECKED_CAST")
    private fun collectAnnotations(beanClass: KClass<*>): Array<T> {
        val annotations = ArrayList<T>()
        beanClass.superClasses { kClass ->
            beanClass.annotations.filter { supportedConditionClass.isInstance(it) }
                .forEach {
                    annotations.add(it as T)
                }
        }
        val array = java.lang.reflect.Array.newInstance(supportedConditionClass.java, annotations.size) as Array<T>
        return annotations.toArray(array)
    }

    @Suppress("UNCHECKED_CAST")
    open fun annClass(): KClass<T> {
        for (supertype in this::class.supertypes) {
            val classifier = supertype.classifier
            if (classifier != AnnotatedCondition::class) {
                continue
            }
            return supertype.arguments[0].type!!.classifier as KClass<T>
        }
        throw IllegalArgumentException("failed get condition class of ${this::class}")
    }

    protected abstract fun matches(actionContext: ActionContext, annotation: T): Boolean
}
