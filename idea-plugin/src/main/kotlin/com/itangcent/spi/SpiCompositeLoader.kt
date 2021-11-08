package com.itangcent.spi

import com.itangcent.common.spi.SpiUtils
import com.itangcent.condition.ConditionEvaluator
import com.itangcent.condition.Exclusion
import com.itangcent.intellij.context.ActionContext
import com.itangcent.order.order
import com.itangcent.utils.superClasses
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

object SpiCompositeLoader {

    /**
     * Load available beans of special type [T].
     * All the beans returned are all initialized by [actionContext].
     */
    inline fun <reified T : Any> load(actionContext: ActionContext): Array<T> {
        val conditionEvaluator = actionContext.instance(ConditionEvaluator::class)
        var matchedClasses = SpiUtils.loadServices(T::class)!!
            .map { it::class }
            .filter {
                conditionEvaluator.matches(actionContext, it)
            }

        //support @Exclusion
        val exclusions = collectExclusions(matchedClasses)
        if (exclusions.isNotEmpty()) {
            matchedClasses = matchedClasses.filter { !exclusions.contains(it) }
        }

        LOG.info("matched ${T::class}:${matchedClasses}")
        return matchedClasses
            .map { actionContext.instance(it) }
            .sortedBy { it.order() }
            .toTypedArray()
    }

    /**
     * Collect all annotated [Exclusion] from matchedClasses and their superclasses.
     */
    fun collectExclusions(matchedClasses: List<KClass<*>>): LinkedList<KClass<*>> {
        val exclusions = LinkedList<KClass<*>>()
        for (matchedClass in matchedClasses) {
            matchedClass.superClasses { kClass ->
                kClass.findAnnotation<Exclusion>()
                    ?.value?.forEach { exclusions.add(it) }
            }
        }
        return exclusions
    }

    val LOG = org.apache.log4j.Logger.getLogger(SpiCompositeLoader::class.java)!!
}
