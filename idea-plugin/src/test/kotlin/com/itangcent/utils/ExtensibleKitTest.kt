package com.itangcent.utils

import com.itangcent.common.model.*
import com.itangcent.common.utils.Extensible
import com.itangcent.common.utils.SimpleExtensible
import com.itangcent.utils.ExtensibleKit.fromJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource


/**
 * Test case for [ExtensibleKit]
 */
class ExtensibleTest {

    class Person(
        val name: String,
        val age: Int
    ) : SimpleExtensible() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Person

            if (name != other.name) return false
            if (age != other.age) return false
            return exts() == other.exts()
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + age
            result = 31 * result + exts().hashCode()
            return result
        }
    }

    @Test
    fun `fromJson deserializes JSON into an Extensible object`() {
        val json = """{
            "name": "John",
            "age": 30,
            "@ext1": "value1",
            "@ext2": "value2"
        }"""
        val expected = Person("John", 30).apply {
            setExt("@ext1", "value1")
            setExt("@ext2", "value2")
        }
        val result = Person::class.fromJson(json)
        assertEquals(expected, result)
    }

    @Test
    fun `fromJson deserializes JSON into an Extensible object with selected attributes`() {
        val json = """{
            "name": "John",
            "age": 30,
            "@ext1": "value1",
            "@ext2": "value2",
            "ext3": "value3"
        }"""
        val expected = Person("John", 30).apply {
            setExt("@ext1", "value1")
            setExt("@ext3", "value3")
        }
        val result = Person::class.fromJson(json, "@ext1", "ext3")
        assertEquals(expected, result)
    }

    @ParameterizedTest
    @ValueSource(
        classes = [Doc::class, FormParam::class,
            Header::class, MethodDoc::class,
            Param::class, PathParam::class,
            Request::class, Response::class
        ]
    )
    fun testSetExts(cls: Class<Extensible>) {
        val extensible = cls.newInstance() as Extensible//{}
        extensible.setExts(mapOf("a" to 1, "b" to 2))
        kotlin.test.assertEquals(mapOf("a" to 1, "b" to 2), extensible.exts())
    }
}