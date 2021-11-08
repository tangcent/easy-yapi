package com.itangcent.condition

import kotlin.reflect.KClass

interface ConditionSupported {

    fun supported(beanClass: KClass<*>): Boolean
}