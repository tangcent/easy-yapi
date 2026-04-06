package com.itangcent.easyapi.core.di

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.cache.CacheService
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.DefaultConfigReader
import com.itangcent.easyapi.exporter.core.EmptyMethodFilter
import com.itangcent.easyapi.exporter.core.MethodFilter
import com.itangcent.easyapi.logging.IdeaConsole
import com.itangcent.easyapi.logging.IdeaConsoleProvider
import com.itangcent.easyapi.logging.IdeaLogConsole
import com.itangcent.easyapi.psi.helper.AnnotationHelper
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.rule.parser.RuleParser
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings
import kotlin.reflect.KClass

/**
 * A lightweight dependency injection container for managing service bindings.
 *
 * OperationScope provides a simple DI mechanism that supports:
 * - Direct instance binding
 * - Lazy binding (provider functions)
 * - Auto-creation of instances via constructor injection
 * - Integration with Java SPI (ServiceLoader)
 *
 * ## Usage
 * ```kotlin
 * // Create a scope with bindings
 * val scope = OperationScope.builder()
 *     .bind(myService)
 *     .bind(OtherService::class, otherService)
 *     .bindLazy(ExpensiveService::class) { ExpensiveService() }
 *     .addSpiBindings(settings)
 *     .build()
 *
 * // Retrieve instances
 * val service = scope.get<MyService>()
 * val optional = scope.getOrNull<OptionalService>()
 * ```
 *
 * @see ActionContext for coroutine-aware context management
 */
interface OperationScope {
    /**
     * Retrieves an instance of the specified type.
     *
     * @param T The type of instance to retrieve
     * @param kClass The KClass of the type
     * @return The instance of type T
     * @throws OperationScopeException if no binding exists for T
     */
    fun <T : Any> get(kClass: KClass<T>): T

    /**
     * Retrieves an instance of the specified type, or null if not bound.
     *
     * @param T The type of instance to retrieve
     * @param kClass The KClass of the type
     * @return The instance of type T, or null if not bound
     */
    fun <T : Any> getOrNull(kClass: KClass<T>): T?

    /**
     * Checks if a binding exists for the specified type.
     *
     * @param kClass The KClass to check
     * @return true if a binding exists
     */
    fun contains(kClass: KClass<*>): Boolean

    companion object {
        /**
         * Creates a new builder for constructing OperationScope instances.
         *
         * @return A new Builder
         */
        fun builder(): Builder = Builder()
    }

    /**
     * Builder for constructing OperationScope instances.
     *
     * Provides a fluent API for registering service bindings.
     */
    class Builder {
        private val bindings = LinkedHashMap<KClass<*>, Any>()

        /**
         * Binds an instance to its own type.
         *
         * @param T The type of the instance
         * @param instance The instance to bind
         * @return This builder for chaining
         */
        fun <T : Any> bind(instance: T): Builder = apply {
            bindings[instance::class] = instance
        }

        /**
         * Binds an instance to a specific type.
         *
         * @param T The type to bind to
         * @param kClass The KClass of the type
         * @param instance The instance to bind
         * @return This builder for chaining
         */
        fun <T : Any> bind(kClass: KClass<T>, instance: T): Builder = apply {
            bindings[kClass] = instance
        }

        /**
         * Binds a lazy provider to a type.
         *
         * The provider will be called only when the type is first requested.
         *
         * @param T The type to bind to
         * @param kClass The KClass of the type
         * @param provider The function that provides the instance
         * @return This builder for chaining
         */
        fun <T : Any> bindLazy(kClass: KClass<T>, provider: () -> T): Builder = apply {
            bindings[kClass] = lazy { provider() }
        }

        /**
         * Adds standard SPI bindings for common services.
         *
         * This includes SettingBinder, ConfigReader, CacheService, IdeaConsole,
         * MethodFilter, AnnotationHelper, DocHelper, and RuleParser.
         *
         * @param settings Optional settings for filtering SPI implementations
         * @return This builder for chaining
         */
        fun addSpiBindings(settings: Settings? = null): Builder = apply {
            val project = bindings[Project::class] as? Project
            if (project != null) {
                if (!bindings.containsKey(SettingBinder::class)) {
                    bindLazy(SettingBinder::class) { SettingBinder.getInstance(project) }
                }

                if (!bindings.containsKey(ConfigReader::class)) {
                    bindLazy(ConfigReader::class) {
                        DefaultConfigReader.getInstance(project)
                    }
                }

                if (!bindings.containsKey(CacheService::class)) {
                    bindLazy(CacheService::class) { CacheService.getInstance(project) }
                }
            }

            if (!bindings.containsKey(IdeaConsole::class)) {
                bindLazy(IdeaConsole::class) {
                    project?.let { IdeaConsoleProvider.getInstance(it) }?.getConsole()
                        ?: IdeaLogConsole
                }
            }

            if (!bindings.containsKey(MethodFilter::class)) {
                bindings[MethodFilter::class] = lazy {
                    SpiCompositeLoader.loadFiltered<MethodFilter>(settings).firstOrNull()
                        ?: EmptyMethodFilter()
                }
            }

            if (!bindings.containsKey(AnnotationHelper::class)) {
                bindings[AnnotationHelper::class] = lazy { UnifiedAnnotationHelper() }
            }

            if (!bindings.containsKey(DocHelper::class)) {
                bindings[DocHelper::class] = lazy {
                    project?.let { StandardDocHelper.getInstance(it) } ?: StandardDocHelper()
                }
            }

            if (!bindings.containsKey(RuleParser::class)) {
                SpiCompositeLoader.loadFiltered<RuleParser>(settings).firstOrNull()?.let { parser ->
                    bindings[RuleParser::class] = parser
                }
            }
        }

        /**
         * Builds and returns a new OperationScope instance.
         *
         * @return A new OperationScope with the configured bindings
         */
        fun build(): OperationScope = DefaultOperationScope(bindings.toMap())
    }
}

/**
 * Reified version of [get] for convenient inline access.
 */
inline fun <reified T : Any> OperationScope.get(): T = get(T::class)

/**
 * Reified version of [getOrNull] for convenient inline access.
 */
inline fun <reified T : Any> OperationScope.getOrNull(): T? = getOrNull(T::class)

/**
 * Exception thrown when a requested type is not bound in the OperationScope.
 */
class OperationScopeException(message: String) : RuntimeException(message)

/**
 * Set of simple types that should not be auto-created.
 */
private val SIMPLE_TYPES: Set<KClass<*>> = setOf(
    String::class,
    Int::class,
    Long::class,
    Double::class,
    Float::class,
    Boolean::class,
    Byte::class,
    Short::class,
    Char::class,
    List::class,
    Set::class,
    Map::class
)

private class DefaultOperationScope(private val bindings: Map<KClass<*>, Any>) : OperationScope {

    private val dynamicCache = java.util.concurrent.ConcurrentHashMap<KClass<*>, Any>()

    override fun <T : Any> get(kClass: KClass<T>): T {
        return getOrNull(kClass) ?: throw OperationScopeException("No binding for ${kClass.qualifiedName}")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOrNull(kClass: KClass<T>): T? {
        val raw = bindings[kClass]
        if (raw != null) {
            val value = if (raw is Lazy<*>) raw.value else raw
            return value as? T
        }
        return dynamicCache.getOrPut(kClass) { autoCreate(kClass) ?: return null } as? T
    }

    override fun contains(kClass: KClass<*>): Boolean =
        bindings.containsKey(kClass) || dynamicCache.containsKey(kClass)

    private fun <T : Any> autoCreate(kClass: KClass<T>): T? {
        if (kClass.isAbstract) return null
        if (kClass in SIMPLE_TYPES) return null
        val constructors = kClass.constructors
        if (constructors.isEmpty()) return null

        for (ctor in constructors.sortedByDescending { it.parameters.size }) {
            val argMap = LinkedHashMap<kotlin.reflect.KParameter, Any?>()
            var satisfied = true
            for (param in ctor.parameters) {
                val paramClass = param.type.classifier as? KClass<*>
                if (paramClass == null) {
                    if (param.isOptional) continue
                    satisfied = false; break
                }
                val arg = resolveParam(paramClass)
                if (arg != null) {
                    argMap[param] = arg
                } else if (param.type.isMarkedNullable) {
                    argMap[param] = null
                } else if (param.isOptional) {
                } else {
                    satisfied = false; break
                }
            }
            if (satisfied) {
                return runCatching { ctor.callBy(argMap) }.getOrNull()
            }
        }
        return null
    }

    private fun resolveParam(kClass: KClass<*>): Any? {
        if (kClass == OperationScope::class) return this
        val raw = bindings[kClass] ?: return null
        return if (raw is Lazy<*>) raw.value else raw
    }
}
