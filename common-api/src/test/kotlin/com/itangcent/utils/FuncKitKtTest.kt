package com.itangcent.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FuncKitKtTest {

    @Test
    fun `test and function`() {
        val isEven = { num: Int -> num % 2 == 0 }
        val isPositive = { num: Int -> num > 0 }

        val isEvenAndPositive = isEven.and(isPositive)

        assertEquals(true, isEvenAndPositive(4))
        assertEquals(false, isEvenAndPositive(3))
        assertEquals(false, isEvenAndPositive(-4))
        assertEquals(false, isEvenAndPositive(0))
    }

    @Test
    fun `test then function with one parameter`() {
        var result = ""

        val addHello = { str: String -> result = "$result $str Hello" }
        val addWorld = { str: String -> result = "$result $str World" }

        val addHelloThenWorld = addHello.then(addWorld)

        result = ""
        addHelloThenWorld("Hi")
        assertEquals(" Hi Hello Hi World", result)

        result = ""
        addHelloThenWorld("")
        assertEquals("  Hello  World", result)
    }

    @Test
    fun `test then function with three parameters`() {
        var result = ""

        val addHello = { str: String, num: Int, flag: Boolean -> result = "$result $str Hello $num $flag" }
        val addWorld = { str: String, num: Int, flag: Boolean -> result = "$result $str World $num $flag" }

        val addHelloThenWorld = addHello.then(addWorld)

        result = ""
        addHelloThenWorld("Hi", 42, true)
        assertEquals(" Hi Hello 42 true Hi World 42 true", result)

        result = ""
        addHelloThenWorld("", 0, false)
        assertEquals("  Hello 0 false  World 0 false", result)
    }
}