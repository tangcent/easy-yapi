package com.itangcent.common.http

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EntityUtilsTest {

    @Test
    fun generateBoundary() {
        val set = HashSet<String>()
        for (i in 0..100) {
            val boundary = EntityUtils.generateBoundary()
            assertNotNull(boundary)
            //length of  boundary should be a random size from 30 to 40
            assertTrue(boundary.length >= 30)
            assertTrue(boundary.length <= 40)
            assertTrue(set.add(boundary))
        }
    }
}