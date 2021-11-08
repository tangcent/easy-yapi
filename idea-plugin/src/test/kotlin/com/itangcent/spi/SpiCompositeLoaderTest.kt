package com.itangcent.spi

import com.itangcent.common.utils.newInstance
import com.itangcent.condition.ConditionEvaluator
import com.itangcent.condition.Exclusion
import com.itangcent.intellij.context.ActionContext
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * Test case of [SpiCompositeLoader]
 */
internal class SpiCompositeLoaderTest {

    @Test
    fun testLoad() {
        val conditionEvaluator = mock<ConditionEvaluator> {
            this.on { it.matches(any(), any()) }
                .thenReturn(true)
        }
        val actionContext = mock<ActionContext> {
            this.on { it.instance(ConditionEvaluator::class) }
                .thenReturn(conditionEvaluator)
            this.on { it.instance(argThat<KClass<*>> { cls -> cls != ConditionEvaluator::class }) }
                .thenAnswer { it.getArgument<KClass<*>>(0).newInstance() }
        }
        val services = SpiCompositeLoader.load<MyService>(actionContext)
        assertEquals("com.itangcent.spi.MyService1", services.joinToString { it::class.qualifiedName!! })
    }
}


interface MyService

@Exclusion(MyService2::class)
class MyService1 : MyService

class MyService2 : MyService