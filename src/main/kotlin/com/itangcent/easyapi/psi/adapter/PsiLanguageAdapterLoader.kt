package com.itangcent.easyapi.psi.adapter

import com.intellij.psi.PsiElement

/**
 * Centralized loader for [PsiLanguageAdapter] instances.
 *
 * Manages the registration and conditional instantiation of language-specific
 * adapters based on the availability of their corresponding language plugins.
 *
 * ## Adapter Loading
 * Each adapter is registered with an [AdapterEntry.onClass] guard string.
 * Before instantiation, [isClassAvailable] checks whether the class is
 * available in the current runtime via `Class.forName()`. This prevents
 * `NoClassDefFoundError` when a language plugin (e.g., Kotlin, Scala, Groovy)
 * is not installed in the IDE.
 *
 * Adapters with `onClass = null` (like [JavaPsiAdapter]) are always loaded.
 *
 * ## Usage
 * ```kotlin
 * // Get all available adapters
 * val adapters = PsiLanguageAdapterLoader.loadAdapters()
 *
 * // Find the adapter for a specific element
 * val adapter = PsiLanguageAdapterLoader.findAdapter(psiElement)
 * ```
 *
 * @see PsiLanguageAdapter for the adapter interface
 */
object PsiLanguageAdapterLoader {

    /**
     * Registration entry for a language adapter.
     *
     * @param onClass Fully qualified class name to check before instantiation,
     *   or `null` if the adapter should always be loaded
     * @param factory Factory function to create the adapter instance
     */
    private data class AdapterEntry(
        val onClass: String?,
        val factory: () -> PsiLanguageAdapter
    )

    /**
     * Registry of all supported language adapters.
     *
     * Order matters: adapters are checked in registration order by [findAdapter].
     * More specific adapters (Kotlin, Scala, Groovy) should come after
     * the general Java adapter, as [findAdapter] returns the first match.
     */
    private val registry = listOf(
        AdapterEntry(onClass = null, factory = { JavaPsiAdapter() }),
        AdapterEntry(onClass = "org.jetbrains.kotlin.idea.KotlinLanguage", factory = { KotlinPsiAdapter() }),
        AdapterEntry(onClass = "org.jetbrains.plugins.scala.lang.ScalaLanguage", factory = { ScalaPsiAdapter() }),
        AdapterEntry(onClass = "org.jetbrains.plugins.groovy.GroovyLanguage", factory = { GroovyPsiAdapter() })
    )

    /**
     * Lazily loaded list of available adapters.
     *
     * Filters the [registry] by checking [isClassAvailable] for each entry's
     * [AdapterEntry.onClass], then instantiates the surviving adapters.
     */
    private val adapters: List<PsiLanguageAdapter> by lazy {
        registry
            .filter { it.onClass == null || isClassAvailable(it.onClass) }
            .map { it.factory() }
    }

    /**
     * Returns all available language adapters for the current IDE installation.
     *
     * Adapters whose [onClass] guard class is not available are excluded.
     */
    fun loadAdapters(): List<PsiLanguageAdapter> = adapters

    /**
     * Finds the first adapter that supports the given [element].
     *
     * @param element The PSI element to find an adapter for
     * @return The first supporting adapter, or `null` if none matches
     */
    fun findAdapter(element: PsiElement): PsiLanguageAdapter? {
        return adapters.firstOrNull { it.supportsElement(element) }
    }

    /**
     * Checks whether a class is available in the current runtime.
     *
     * Uses `Class.forName()` to test availability without triggering
     * static initialization of the target class.
     *
     * @param fqn Fully qualified class name to check
     * @return `true` if the class is available, `false` otherwise
     */
    private fun isClassAvailable(fqn: String): Boolean {
        return runCatching { Class.forName(fqn) }.isSuccess
    }
}
