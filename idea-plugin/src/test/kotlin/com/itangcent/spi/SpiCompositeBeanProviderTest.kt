package com.itangcent.spi

import com.itangcent.common.spi.ProxyBean
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.order.Order
import com.itangcent.order.Ordered
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test case for [SpiCompositeBeanProvider]
 */
internal class SpiCompositeBeanProviderTest : AdvancedContextTest() {

    @Test
    fun testCompositeProvider() {
        // Create mock implementations
        val service1 = TestService1()
        val service2 = TestService2()
        val services = arrayOf(service1, service2)

        // Create a test provider that directly returns our services
        val provider = TestCompositeProviderWithDirectServices(services)

        // Get the composite service from the provider
        val compositeService = provider.get()

        // Test the composite service with string processing
        assertEquals("TestService1: test data", compositeService.process("test data"))
        assertEquals("TestService1: special", compositeService.process("special"))

        // Test the composite service with list processing
        val callTracker = mutableListOf<String>()
        compositeService.processWithList(callTracker, "test data")

        // Verify both services were called
        assertTrue(callTracker.contains("TestService1"))
        assertTrue(callTracker.contains("TestService2"))
        assertEquals(2, callTracker.size)

        // Test with special data
        callTracker.clear()
        compositeService.processWithList(callTracker, "special")
        assertTrue(callTracker.contains("TestService1-special"))
        assertTrue(callTracker.contains("TestService2-special"))
        assertEquals(2, callTracker.size)
    }

    // Test interfaces and classes
    interface TestService {
        fun process(data: String): String

        // method that updates a list to track which implementations were called
        fun processWithList(callTracker: MutableList<String>, data: String)
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    class TestService1 : TestService {
        override fun process(data: String): String {
            return if (data == "special") {
                "TestService1: special"
            } else {
                "TestService1: $data"
            }
        }

        override fun processWithList(callTracker: MutableList<String>, data: String) {
            if (data == "special") {
                callTracker.add("TestService1-special")
            } else {
                callTracker.add("TestService1")
            }
        }
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    class TestService2 : TestService {
        override fun process(data: String): String {
            return if (data == "special") {
                "TestService2 doesn't handle special"
            } else {
                "TestService2: $data"
            }
        }

        override fun processWithList(callTracker: MutableList<String>, data: String) {
            if (data == "special") {
                callTracker.add("TestService2-special")
            } else {
                callTracker.add("TestService2")
            }
        }
    }

    // Custom implementation that directly returns our test services
    class TestCompositeProviderWithDirectServices(private val services: Array<TestService>) :
        SpiCompositeBeanProvider<TestService>() {
        @Suppress("UNCHECKED_CAST")
        override fun loadBean(actionContext: ActionContext, kClass: KClass<TestService>): TestService {
            // If there's only one service, return it directly
            if (services.size == 1) {
                return services[0]
            }

            // Create a composite proxy that delegates to all services
            return Proxy.newProxyInstance(
                kClass.java.classLoader,
                arrayOf(kClass.java),
                ProxyBean(arrayOf(*services))
            ) as TestService
        }
    }
} 