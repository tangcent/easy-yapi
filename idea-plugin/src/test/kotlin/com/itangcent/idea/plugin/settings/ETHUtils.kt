package com.itangcent.idea.plugin.settings

import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * @author tangcent
 * @date 2024/05/11
 */
object ETHUtils {

    fun <T : Any> testCETH(original: T, copy: T.() -> T) {
        assertEquals(original, original)
        assertNotNull(original, "original should not be null")
        assertNotEquals<Any>(original, "original should not be equals to a string")
        // Create a copy using the data class generated `copy` method
        val copied = original.copy()
        assertEquals(original, copied)
        assertEquals(original.hashCode(), copied.hashCode())
        assertEquals(original.toString(), copied.toString())

        // Use reflection to fetch all properties
        val memberProperties = original::class.memberProperties
        memberProperties.filterIsInstance<KMutableProperty<*>>()
            .forEach { property ->
                val newCopied = original.copy()

                val backup = property.getter.call(newCopied)
                val propClass = property.returnType.classifier as? KClass<*>
                val updateValue = backup?.backup() ?: propClass?.fake()

                property.setter.call(newCopied, updateValue)

                // Check that the modified object does not equal the copied object
                checkEquals(original, newCopied, property, backup, updateValue)

                // Restore original property to continue clean tests
                property.setter.call(original, backup)
            }

        val originalProperties = memberProperties.associate { it.name to it.getter.call(original) }
        val constructor = original::class.constructors.maxByOrNull { it.parameters.size } ?: return
        memberProperties.filter { it !is KMutableProperty<*> }
            .forEach { property ->
                val propertyName = property.name
                val backup = property.getter.call(original)
                val propClass = property.returnType.classifier as? KClass<*>
                val updateValue = backup?.backup() ?: propClass?.fake()

                val newCopied = constructor.callBy(
                    constructor.parameters.associateWith {
                        if (it.name == propertyName) updateValue else originalProperties[it.name]
                    }
                )

                // Check that the modified object does not equal the copied object
                checkEquals(original, newCopied, property, backup, updateValue)
            }
    }

    /**
     * Check that the modified object does not equal the copied object
     */
    private fun checkEquals(
        original: Any,
        newCopied: Any,
        property: KProperty<*>,
        backup: Any?,
        updateValue: Any?
    ) {
        assertNotEquals(
            original,
            newCopied,
            "[${original::class}] Change Property: ${property.name} from $backup to $updateValue}"
        )
        assertNotEquals(
            original.hashCode(), newCopied.hashCode(),
            "[${original::class}] Change Property: ${property.name} from $backup to $updateValue}"
        )
        assertNotEquals(
            original.toString(), newCopied.toString(),
            "[${original::class}] Change Property: ${property.name} from $backup to $updateValue}"
        )
    }
}

@Suppress("UNCHECKED_CAST")
fun Any.backup(): Any {
    return when (this) {
        is Array<*> -> {
            if (this.size > 0) {
                val newArray = this.copyOf(size + 1) as Array<Any?>
                newArray[newArray.lastIndex] = this.first()!!.backup()
                newArray
            } else {
                // get the component type of the array
                val componentType = this::class.java.componentType.kotlin
                val fake = componentType.fake()
                (this.copyOf(1) as Array<Any?>).apply { this[0] = fake }
            }
        }

        is Boolean -> !this
        is String -> "$this modify"
        is Int -> this + 1
        else -> throw IllegalArgumentException("Unsupported type: ${this::class.simpleName}")
    }
}

fun KClass<*>.fake(): Any {
    return when (this) {
        Boolean::class -> false
        String::class -> "fake"
        Int::class -> 0
        else -> throw IllegalArgumentException("Unsupported type: ${this.simpleName}")
    }
}