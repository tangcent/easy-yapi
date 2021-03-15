package com.itangcent.common.http

import java.util.*

object EntityUtils {

    /**
     * The pool of ASCII chars to be used for generating a multipart boundary.
     */
    private val MULTIPART_CHARS = "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            .toCharArray()

    /**
     * @see org.apache.http.entity.mime.MultipartEntity#generateBoundary
     */
    fun generateBoundary(): String {
        val buffer = StringBuilder()
        val rand = Random()
        val count = rand.nextInt(11) + 30 // a random size from 30 to 40
        for (i in 0 until count) {
            buffer.append(MULTIPART_CHARS[rand.nextInt(MULTIPART_CHARS.size)])
        }
        return buffer.toString()
    }

}