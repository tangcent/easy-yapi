package com.itangcent.easyapi.core.di

import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Test

@ConditionOnClass("java.lang.String")
class ClassWithStringCondition

@ConditionOnClass("non.existent.Class")
class ClassWithNonExistentCondition

@ConditionOnMissingClass("java.lang.String")
class ClassWithMissingClassCondition

@ConditionOnMissingClass("non.existent.Class")
class ClassWithNonExistentMissingClassCondition

@ConditionOnSetting("builtInConfig", "test")
class ClassWithSettingCondition

@ConditionOnSetting("builtInConfig", "other")
class ClassWithWrongSettingCondition

class ConditionEvaluatorTest {

    @Test
    fun testEvaluateConditionOnClassExists() {
        assertTrue(ConditionEvaluator.evaluate(ClassWithStringCondition::class))
    }

    @Test
    fun testEvaluateConditionOnClassNotExists() {
        assertFalse(ConditionEvaluator.evaluate(ClassWithNonExistentCondition::class))
    }

    @Test
    fun testEvaluateConditionOnMissingClassExists() {
        assertFalse(ConditionEvaluator.evaluate(ClassWithMissingClassCondition::class))
    }

    @Test
    fun testEvaluateConditionOnMissingClassNotExists() {
        assertTrue(ConditionEvaluator.evaluate(ClassWithNonExistentMissingClassCondition::class))
    }

    @Test
    fun testEvaluateConditionOnSettingMatch() {
        val settings = Settings().apply { builtInConfig = "test" }
        assertTrue(ConditionEvaluator.evaluate(ClassWithSettingCondition::class, settings))
    }

    @Test
    fun testEvaluateConditionOnSettingNoMatch() {
        val settings = Settings().apply { builtInConfig = "test" }
        assertFalse(ConditionEvaluator.evaluate(ClassWithWrongSettingCondition::class, settings))
    }

    @Test
    fun testEvaluateConditionOnSettingNoSettings() {
        assertTrue(ConditionEvaluator.evaluate(ClassWithSettingCondition::class, null))
    }

    @Test
    fun testEvaluateNoConditions() {
        class NoConditionClass
        assertTrue(ConditionEvaluator.evaluate(NoConditionClass::class))
    }

    @Test
    fun testEvaluateMultipleConditions() {
        @ConditionOnClass("java.lang.String")
        @ConditionOnMissingClass("non.existent.Class")
        class MultipleConditionsClass
        assertTrue(ConditionEvaluator.evaluate(MultipleConditionsClass::class))
    }

    @Test
    fun testEvaluateMultipleConditionsOneFails() {
        @ConditionOnClass("java.lang.String")
        @ConditionOnMissingClass("java.lang.String")
        class MixedConditionsClass
        assertFalse(ConditionEvaluator.evaluate(MixedConditionsClass::class))
    }

    @Test
    fun testEvaluateWithRemoteConfigSetting() {
        @ConditionOnSetting("remoteConfig", "value1\nvalue2")
        class RemoteConfigConditionClass

        val settings = Settings().apply { remoteConfig = arrayOf("value1", "value2") }
        assertTrue(ConditionEvaluator.evaluate(RemoteConfigConditionClass::class, settings))
    }
}
