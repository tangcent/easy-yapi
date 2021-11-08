package com.itangcent.condition

import com.itangcent.common.spi.SpiUtils
import com.itangcent.common.utils.newInstance
import com.itangcent.intellij.context.ActionContext
import com.itangcent.utils.superClasses
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class DefaultConditionEvaluator : ConditionEvaluator {


    private val loadedConditions by lazy {
        val conditions = SpiUtils.loadServices(Condition::class)!!
        if (conditions.isEmpty()) {
            return@lazy emptyList<Condition>()
        }
        conditions.forEach {
            conditionBeanCache[it::class] = it
        }
        conditions
    }

    /**
     * cache resolved conditions of the given bean class
     */
    private val resolvedConditions = ConcurrentHashMap<KClass<*>, List<Condition>>()

    /**
     * cache instances of conditions
     */
    private val conditionBeanCache = ConcurrentHashMap<KClass<*>, Condition>()

    override fun matches(actionContext: ActionContext, beanClass: KClass<*>): Boolean {
        return resolvedConditions.computeIfAbsent(beanClass) {
            resolveCondition(it)
        }.all { it.matches(actionContext, beanClass) }
    }

    private fun resolveCondition(beanClass: KClass<*>): List<Condition> {
        val conditions = HashSet<Condition>()
        for (condition in loadedConditions) {
            if (condition !is ConditionSupported) {
                conditions.add(condition)
            } else if (condition.supported(beanClass)) {
                conditions.add(condition)
            }
        }
        beanClass.superClasses { kClass ->
            val conditional = kClass.findAnnotation<Conditional>()
            if (conditional != null) {
                val customizedConditions = conditional.value.map {
                    conditionBeanCache.computeIfAbsent(it) { conditionClass ->
                        conditionClass.newInstance() as Condition
                    }
                }
                if (customizedConditions.isNotEmpty()) {
                    conditions.addAll(customizedConditions)
                }
            }
        }
        return conditions.toList()
    }
}
