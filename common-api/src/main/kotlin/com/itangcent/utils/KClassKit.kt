package com.itangcent.utils

import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses

fun KClass<*>.superClasses(handle: (KClass<*>) -> Unit) {
    handle(this)
    this.allSuperclasses.forEach(handle)
}