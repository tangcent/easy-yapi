package com.itangcent.condition

import com.itangcent.intellij.context.ActionContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.reflect.KClass

class AnnotatedConditionTest {

    @Test
    fun `matches returns true for matching annotation`() {
        val condition = object : AnnotatedCondition<MyAnnotation>() {
            override fun matches(actionContext: ActionContext, annotation: MyAnnotation): Boolean {
                return annotation.value == "test"
            }
        }

        @MyAnnotation("test")
        class MyClass

        assertTrue(condition.matches(mock(), MyClass::class))
    }


    @Test
    fun `matches returns false for non-matching annotation`() {
        val condition = object : AnnotatedCondition<MyAnnotation>() {
            override fun matches(actionContext: ActionContext, annotation: MyAnnotation): Boolean {
                return annotation.value == "test"
            }
        }

        @MyAnnotation("other")
        class MyClass

        assertFalse(condition.matches(mock(), MyClass::class))
    }

    @Test
    fun `matches returns false for any non-matching annotation`() {
        val condition = object : AnnotatedCondition<MyAnnotation>() {
            override fun matches(actionContext: ActionContext, annotation: MyAnnotation): Boolean {
                return annotation.value == "test"
            }
        }

        @MyAnnotation("test")
        open class MyBaseClass

        @MyAnnotation("other")
        class MyClass : MyBaseClass()

        assertFalse(condition.matches(mock(), MyClass::class))
    }

    @Test
    fun `supported returns true for supported bean class`() {
        val condition = object : AnnotatedCondition<MyAnnotation>() {
            override fun matches(actionContext: ActionContext, annotation: MyAnnotation): Boolean {
                return true
            }
        }

        @MyAnnotation("test")
        class MyClass

        assertTrue(condition.supported(MyClass::class))
    }

    @Test
    fun `supported returns false for unsupported bean class`() {
        val condition = object : AnnotatedCondition<MyAnnotation>() {
            override fun matches(actionContext: ActionContext, annotation: MyAnnotation): Boolean {
                return true
            }
        }

        class MyClass

        assertFalse(condition.supported(MyClass::class))
    }

    @Test
    fun `supported returns false for super class`() {
        val condition = object : AnnotatedCondition<MyAnnotation>() {
            override fun matches(actionContext: ActionContext, annotation: MyAnnotation): Boolean {
                return annotation.value == "test"
            }
        }

        @MyAnnotation("test")
        open class MyBaseClass

        class MyClass : MyBaseClass()

        assertTrue(condition.supported(MyClass::class))
    }

    @Test
    fun `annClass returns the correct annotation class for the given type parameter`() {
        val condition = object : AnnotatedCondition<MyAnnotation>() {
            override fun matches(actionContext: ActionContext, annotation: MyAnnotation): Boolean {
                return true
            }

            public override fun annClass(): KClass<MyAnnotation> {
                return super.annClass()
            }
        }

        assertEquals(MyAnnotation::class, condition.annClass())
    }

    @Test
    fun `sub-sub-class returns the correct annotation class for the given type parameter`() {
        abstract class SubAnnotatedCondition : AnnotatedCondition<MyAnnotation>()
        abstract class SubSubAnnotatedCondition : SubAnnotatedCondition()

        val condition = object : SubSubAnnotatedCondition() {
            override fun matches(actionContext: ActionContext, annotation: MyAnnotation): Boolean {
                return annotation.value == "test"
            }

            public override fun annClass(): KClass<MyAnnotation> {
                return super.annClass()
            }
        }

        @MyAnnotation("test")
        class MyClass

        assertEquals(MyAnnotation::class, condition.annClass())

        assertTrue(condition.matches(mock(), MyClass::class))
    }
}


annotation class MyAnnotation(val value: String)