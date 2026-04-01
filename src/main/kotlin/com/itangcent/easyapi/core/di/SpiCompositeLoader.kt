package com.itangcent.easyapi.core.di

import com.itangcent.easyapi.settings.Settings
import java.util.ServiceLoader

/**
 * Loader for SPI (Service Provider Interface) implementations with filtering support.
 *
 * This loader extends Java's [ServiceLoader] with additional features:
 * - Conditional loading based on annotations ([ConditionOnClass], [ConditionOnMissingClass], [ConditionOnSetting])
 * - Exclusion filtering via [Exclusion] annotation
 * - Automatic ordering via [Order] annotation or [Ordered] interface
 *
 * ## Usage
 * ```kotlin
 * // Load all implementations
 * val parsers = SpiCompositeLoader.load<RuleParser>()
 *
 * // Load with condition filtering
 * val exporters = SpiCompositeLoader.loadFiltered<ClassExporter>(settings)
 * ```
 */
object SpiCompositeLoader {
    /**
     * Loads all implementations of the specified service type.
     *
     * Results are sorted by their order value.
     *
     * @param T The service type
     * @param service The service class
     * @param classLoader The class loader to use
     * @return List of implementations, sorted by order
     */
    fun <T : Any> load(service: Class<T>, classLoader: ClassLoader = service.classLoader): List<T> {
        return ServiceLoader.load(service, classLoader).toList().sortedBy { it.order() }
    }

    /**
     * Reified version of [load] for convenient inline access.
     */
    inline fun <reified T : Any> load(classLoader: ClassLoader = T::class.java.classLoader): List<T> {
        return load(T::class.java, classLoader)
    }

    /**
     * Loads implementations with condition and exclusion filtering.
     *
     * Implementations are filtered based on:
     * 1. Conditional annotations ([ConditionOnClass], [ConditionOnMissingClass], [ConditionOnSetting])
     * 2. Exclusion annotations ([Exclusion])
     *
     * @param T The service type
     * @param service The service class
     * @param classLoader The class loader to use
     * @param settings Optional settings for condition evaluation
     * @return List of filtered implementations, sorted by order
     */
    fun <T : Any> loadFiltered(
        service: Class<T>,
        classLoader: ClassLoader = service.classLoader,
        settings: Settings? = null
    ): List<T> {
        var matched = load(service, classLoader)
            .filter { impl -> ConditionEvaluator.evaluate(impl::class, settings) }

        if (matched.isEmpty()) return emptyList()

        val exclusions = collectExclusions(matched.map { it::class.java })
        if (exclusions.isNotEmpty()) {
            matched = matched.filter { !exclusions.contains(it::class.java) }
        }

        return matched
    }

    /**
     * Reified version of [loadFiltered] for convenient inline access.
     */
    inline fun <reified T : Any> loadFiltered(settings: Settings? = null): List<T> {
        return loadFiltered(T::class.java, T::class.java.classLoader, settings)
    }

    /**
     * Collects all excluded classes from the matched implementations.
     */
    private fun collectExclusions(matchedClasses: List<Class<*>>): Set<Class<*>> {
        val exclusions = LinkedHashSet<Class<*>>()
        for (matchedClass in matchedClasses) {
            for (type in superTypes(matchedClass)) {
                val exclusion = type.getAnnotation(Exclusion::class.java) ?: continue
                for (kClass in exclusion.value) {
                    exclusions.add(kClass.java)
                }
            }
        }
        return exclusions
    }

    private fun superTypes(clazz: Class<*>): Sequence<Class<*>> = sequence {
        val visited = HashSet<Class<*>>()
        val queue = ArrayDeque<Class<*>>()
        queue.add(clazz)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            yield(current)
            current.superclass?.let { queue.add(it) }
            current.interfaces.forEach { queue.add(it) }
        }
    }
}
