package com.itangcent.idea.utils

import java.security.MessageDigest
import kotlin.text.Charsets

/**
 * Utility functions for message digest operations
 */
object DigestUtils {

    /**
     * Calculates the MD5 hash of the given string
     *
     * @param input The string to hash
     * @return The MD5 hash as a hexadecimal string
     */
    fun md5(input: String): String {
        return md5(input.toByteArray(Charsets.UTF_8))
    }

    /**
     * Calculates the MD5 hash of the given byte array
     *
     * @param input The byte array to hash
     * @return The MD5 hash as a hexadecimal string
     */
    fun md5(input: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input)
        return digest.joinToString("") { "%02x".format(it) }
    }
} 