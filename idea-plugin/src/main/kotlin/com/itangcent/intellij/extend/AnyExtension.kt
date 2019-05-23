package com.itangcent.intellij.extend

import com.itangcent.intellij.extend.ReflectTools.getClassPropertyValueByName

/**
 *
 * copy from https://github.com/tangcent/intellij-kotlin
 * It will be removed for next version intellij-kotlin
 */

/**
 * get object property value by name
 */
fun Any.getPropertyValue(propertyName: String): Any? {
    return getClassPropertyValueByName(this, propertyName)
}