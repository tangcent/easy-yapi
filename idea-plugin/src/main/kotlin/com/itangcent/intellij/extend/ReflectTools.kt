package com.itangcent.intellij.extend


/**
 * copy from https://github.com/tangcent/intellij-kotlin
 * It will be removed for next version intellij-kotlin
 */
object ReflectTools {
    /**
     * get the property value from a class object ,no matter whether the property is public ,private or intenel
     */
    fun getClassPropertyValueByName(classObj: Any, propertyName: String): Any? {
        val containerClass: Class<*> = classObj::class.java

        var tobeSearchMethodClass: Class<*>? = containerClass

        while (tobeSearchMethodClass != null) {

            tobeSearchMethodClass.declaredFields.forEach { field ->
                if (field.name == propertyName) {
                    field.isAccessible = true

                    return field.get(classObj)
                }
            }
            tobeSearchMethodClass = tobeSearchMethodClass.superclass
        }
        return null
    }
}