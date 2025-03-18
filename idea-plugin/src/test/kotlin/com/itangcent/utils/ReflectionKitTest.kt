package com.itangcent.utils

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Test case for [ReflectionKit]
 */
internal class ReflectionKitTest {

    @Test
    fun testFindGenericType() {
        // Test finding generic type from direct subclass
        val directSubclassType = DirectSubclass::class.java
            .findGenericType<TestService>(TestBaseClass::class.java)
        assertEquals(TestService::class, directSubclassType)

        // Test finding generic type from indirect subclass
        val indirectSubclassType = IndirectSubclass::class.java
            .findGenericType<TestService>(TestBaseClass::class.java)
        assertEquals(TestService::class, indirectSubclassType)

        // Test finding generic type from nested generic type
        val nestedGenericType = NestedGenericSubclass::class.java
            .findGenericType<List<*>>(TestBaseClass::class.java)
        assertEquals(List::class, nestedGenericType)

        // Test finding second type parameter
        val secondTypeParam = MultiTypeParamClass::class.java
            .findGenericType<String>(MultiTypeParamBaseClass::class.java, 1)
        assertEquals(String::class, secondTypeParam)
    }

    @Test
    fun testFindGenericTypeFromInterface() {
        // Test finding generic type from direct interface implementation
        val directInterfaceType = InterfaceImpl::class.java
            .findGenericTypeFromInterface<TestService>(TestGenericInterface::class.java)
        assertEquals(TestService::class, directInterfaceType)

        // Test finding generic type from indirect interface implementation
        val indirectInterfaceType = IndirectInterfaceImpl::class.java
            .findGenericTypeFromInterface<TestService>(TestGenericInterface::class.java)
        assertEquals(TestService::class, indirectInterfaceType)

        // Test finding generic type from interface with multiple type parameters
        val secondInterfaceTypeParam = MultiTypeParamInterfaceImpl::class.java
            .findGenericTypeFromInterface<String>(MultiTypeParamInterface::class.java, 1)
        assertEquals(String::class, secondInterfaceTypeParam)

        // Test when interface is not found
        val notFoundType = NoInterface::class.java
            .findGenericTypeFromInterface<Any>(TestGenericInterface::class.java)
        assertNull(notFoundType)
    }

    // Test classes for class hierarchy
    interface TestService

    open class TestBaseClass<T : Any>

    // Direct subclass of TestBaseClass
    class DirectSubclass : TestBaseClass<TestService>()

    // Indirect subclass of TestBaseClass
    open class IntermediateClass<T : Any> : TestBaseClass<T>()
    class IndirectSubclass : IntermediateClass<TestService>()

    // Nested generic type
    class NestedGenericSubclass : TestBaseClass<List<String>>()

    // Multiple type parameters
    open class MultiTypeParamBaseClass<T : Any, U : Any>
    class MultiTypeParamClass : MultiTypeParamBaseClass<TestService, String>()

    // Test classes for interface hierarchy
    interface TestGenericInterface<T : Any>

    // Direct interface implementation
    class InterfaceImpl : TestGenericInterface<TestService>

    // Indirect interface implementation
    interface IntermediateInterface<T : Any> : TestGenericInterface<T>
    class IndirectInterfaceImpl : IntermediateInterface<TestService>

    // Interface with multiple type parameters
    interface MultiTypeParamInterface<T : Any, U : Any>
    class MultiTypeParamInterfaceImpl : MultiTypeParamInterface<TestService, String>

    // Class with no relevant interface
    class NoInterface
}