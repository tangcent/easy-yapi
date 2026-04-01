package com.itangcent.easyapi.core.di

import com.itangcent.easyapi.settings.Settings

/**
 * Provider for loading a single SPI implementation.
 *
 * Unlike [SpiCompositeLoader] which returns all implementations,
 * this provider returns only the first (highest priority) implementation.
 *
 * ## Usage
 * ```kotlin
 * val provider = SpiSingleBeanProvider(MyService::class.java)
 * val service = provider.get() // Returns first implementation or null
 * val filteredService = provider.getFiltered(settings) // With condition filtering
 * ```
 *
 * @param T The service type
 * @param service The service class
 * @param classLoader The class loader to use
 */
class SpiSingleBeanProvider<T : Any>(
    private val service: Class<T>,
    private val classLoader: ClassLoader = service.classLoader
) {
    /**
     * Returns the first implementation of the service type, or null if none found.
     *
     * @return The first implementation, or null
     */
    fun get(): T? = SpiCompositeLoader.load(service, classLoader).firstOrNull()

    /**
     * Returns the first filtered implementation of the service type, or null if none found.
     *
     * Applies condition and exclusion filtering.
     *
     * @param settings Optional settings for condition evaluation
     * @return The first filtered implementation, or null
     */
    fun getFiltered(settings: Settings? = null): T? =
        SpiCompositeLoader.loadFiltered(service, classLoader, settings).firstOrNull()
}
