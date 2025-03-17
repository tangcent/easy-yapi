package com.itangcent.utils

import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

/**
 * Utility class for reflection operations.
 */
object ReflectionKit

/**
 * Recursively find the generic type parameter T from the class hierarchy.
 *
 * @param targetClass The target class to check against (e.g., a base class or interface)
 * @param typeIndex The index of the type parameter to retrieve (default is 0)
 * @return The KClass representing the generic type parameter T
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> Class<*>.findGenericType(
    targetClass: Class<*>,
    typeIndex: Int = 0
): KClass<T> {
    val superclass = this.genericSuperclass
        ?: throw IllegalStateException("Cannot determine the generic type for ${this.name}")

    return when {
        // If we found a ParameterizedType (like BaseClass<MyType>)
        superclass is ParameterizedType -> {
            val rawType = superclass.rawType as Class<*>

            // Check if this is the target class or a subclass of it
            if (targetClass.isAssignableFrom(rawType)) {
                val typeArguments = superclass.actualTypeArguments
                if (typeIndex >= typeArguments.size) {
                    throw IllegalArgumentException("Type index $typeIndex is out of bounds for class $this with ${typeArguments.size} type parameters")
                }

                val typeArgument = typeArguments[typeIndex]
                if (typeArgument is Class<*>) {
                    return typeArgument.kotlin as KClass<T>
                } else if (typeArgument is ParameterizedType) {
                    // Handle nested generic types like BaseClass<List<String>>
                    return (typeArgument.rawType as Class<*>).kotlin as KClass<T>
                }
            }

            // If we're at a different generic class in the hierarchy, continue searching upward
            rawType.findGenericType(targetClass, typeIndex)
        }
        // If we found a regular Class, continue searching upward
        else -> (superclass as Class<*>).findGenericType(targetClass, typeIndex)
    }
}

/**
 * Find the generic type parameter T from a class that implements an interface.
 *
 * @param interfaceClass The interface class to check against
 * @param typeIndex The index of the type parameter to retrieve (default is 0)
 * @return The KClass representing the generic type parameter T, or null if not found
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> Class<*>.findGenericTypeFromInterface(
    interfaceClass: Class<*>,
    typeIndex: Int = 0
): KClass<T>? {
    // Check all generic interfaces implemented by this class
    for (genericInterface in this.genericInterfaces) {
        if (genericInterface is ParameterizedType) {
            val rawType = genericInterface.rawType as Class<*>
            if (interfaceClass.isAssignableFrom(rawType)) {
                val typeArguments = genericInterface.actualTypeArguments
                if (typeIndex < typeArguments.size) {
                    val typeArgument = typeArguments[typeIndex]
                    if (typeArgument is Class<*>) {
                        return typeArgument.kotlin as KClass<T>
                    } else if (typeArgument is ParameterizedType) {
                        return (typeArgument.rawType as Class<*>).kotlin as KClass<T>
                    }
                }
            }
        }
    }

    // If not found in direct interfaces, check the superclass
    val superclass = this.superclass ?: return null
    return superclass.findGenericTypeFromInterface(interfaceClass, typeIndex)
}

/**
 * Recursively find the generic type parameter T from the class hierarchy using KClass.
 *
 * @param targetClass The target class to check against (e.g., a base class or interface)
 * @param typeIndex The index of the type parameter to retrieve (default is 0)
 * @return The KClass representing the generic type parameter T
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> KClass<*>.findGenericType(
    targetClass: KClass<*>,
    typeIndex: Int = 0
): KClass<T> {
    return this.java.findGenericType(targetClass.java, typeIndex)
}

/**
 * Find the generic type parameter T from a class that implements an interface using KClass.
 *
 * @param interfaceClass The interface class to check against
 * @param typeIndex The index of the type parameter to retrieve (default is 0)
 * @return The KClass representing the generic type parameter T, or null if not found
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> KClass<*>.findGenericTypeFromInterface(
    interfaceClass: KClass<*>,
    typeIndex: Int = 0
): KClass<T>? {
    return this.java.findGenericTypeFromInterface(interfaceClass.java, typeIndex)
}