package com.itangcent.idea.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.text.Charsets

/**
 * Test cases for [DigestUtils]
 */
class DigestUtilsTest {
    @Test
    fun testMd5FromString() {
        // Test empty string
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", DigestUtils.md5(""))

        // Test simple string
        assertEquals("5d41402abc4b2a76b9719d911017c592", DigestUtils.md5("hello"))

        // Test longer string with various characters
        assertEquals("9e107d9d372bb6826bd81d3542a419d6", DigestUtils.md5("The quick brown fox jumps over the lazy dog"))

        // Test string with special characters
        assertEquals("933eb153ef15b4800452a52cbd0cffe1", DigestUtils.md5("Test@123!"))

        // Test string with non-ASCII characters
        assertEquals("c0e89a293bd36c7a768e4e9d2c5475a8", DigestUtils.md5("こんにちは"))
    }

    @Test
    fun testMd5FromByteArray() {
        // Test empty byte array
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", DigestUtils.md5(ByteArray(0)))

        // Test simple byte array
        assertEquals("5d41402abc4b2a76b9719d911017c592", DigestUtils.md5("hello".toByteArray(Charsets.UTF_8)))

        // Test longer byte array
        assertEquals(
            "9e107d9d372bb6826bd81d3542a419d6",
            DigestUtils.md5("The quick brown fox jumps over the lazy dog".toByteArray(Charsets.UTF_8))
        )

        // Test byte array with special characters
        assertEquals("933eb153ef15b4800452a52cbd0cffe1", DigestUtils.md5("Test@123!".toByteArray(Charsets.UTF_8)))

        // Test byte array with non-ASCII characters
        assertEquals("c0e89a293bd36c7a768e4e9d2c5475a8", DigestUtils.md5("こんにちは".toByteArray(Charsets.UTF_8)))

        // Test raw byte array with specific values
        assertEquals("7cfdd07889b3295d6a550914ab35e068", DigestUtils.md5(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)))
    }
}