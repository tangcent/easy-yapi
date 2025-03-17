package com.itangcent.utils

import kotlin.reflect.KClass

/**
 * Utility class for array operations.
 */
object ArrayKit {
    
    /**
     * Creates a typed array of the specified class with the given elements.
     * 
     * @param clazz The class of the array elements
     * @param elements The elements to be placed in the array
     * @return A typed array containing the elements
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> toArray(clazz: Class<T>, elements: List<T>): Array<T> {
        return java.lang.reflect.Array.newInstance(clazz, elements.size).apply {
            elements.forEachIndexed { index, element ->
                java.lang.reflect.Array.set(this, index, element)
            }
        } as Array<T>
    }
    
    /**
     * Creates a typed array of the specified KClass with the given elements.
     * 
     * @param kClass The KClass of the array elements
     * @param elements The elements to be placed in the array
     * @return A typed array containing the elements
     */
    fun <T : Any> toArray(kClass: KClass<T>, elements: List<T>): Array<T> {
        return toArray(kClass.java, elements)
    }
    
    /**
     * Creates an empty typed array of the specified class.
     * 
     * @param clazz The class of the array elements
     * @return An empty typed array
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> emptyArray(clazz: Class<T>): Array<T> {
        return java.lang.reflect.Array.newInstance(clazz, 0) as Array<T>
    }
    
    /**
     * Creates an empty typed array of the specified KClass.
     * 
     * @param kClass The KClass of the array elements
     * @return An empty typed array
     */
    fun <T : Any> emptyArray(kClass: KClass<T>): Array<T> {
        return emptyArray(kClass.java)
    }
} 