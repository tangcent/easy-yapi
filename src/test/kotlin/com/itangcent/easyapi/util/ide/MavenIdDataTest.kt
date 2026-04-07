package com.itangcent.easyapi.util.ide

import org.junit.Assert.*
import org.junit.Test

class MavenIdDataTest {

    private val data = MavenIdData("com.example", "my-lib", "1.0.0")

    @Test
    fun testProperties() {
        assertEquals("com.example", data.groupId)
        assertEquals("my-lib", data.artifactId)
        assertEquals("1.0.0", data.version)
    }

    @Test
    fun testMaven() {
        val xml = data.maven()
        assertTrue(xml.contains("<groupId>com.example</groupId>"))
        assertTrue(xml.contains("<artifactId>my-lib</artifactId>"))
        assertTrue(xml.contains("<version>1.0.0</version>"))
        assertTrue(xml.contains("<dependency>"))
    }

    @Test
    fun testGradleKotlin() {
        val result = data.gradleKotlin()
        assertEquals("implementation(\"com.example:my-lib:1.0.0\")", result)
    }

    @Test
    fun testGradleGroovy() {
        val result = data.gradleGroovy()
        assertEquals("implementation 'com.example:my-lib:1.0.0'", result)
    }

    @Test
    fun testSbt() {
        val result = data.sbt()
        assertEquals("libraryDependencies += \"com.example\" % \"my-lib\" % \"1.0.0\"", result)
    }

    @Test
    fun testEquality() {
        val d1 = MavenIdData("com.example", "my-lib", "1.0.0")
        val d2 = MavenIdData("com.example", "my-lib", "1.0.0")
        assertEquals(d1, d2)
        assertEquals(d1.hashCode(), d2.hashCode())
    }

    @Test
    fun testInequality() {
        val d1 = MavenIdData("com.example", "my-lib", "1.0.0")
        val d2 = MavenIdData("com.example", "my-lib", "2.0.0")
        assertNotEquals(d1, d2)
    }

    @Test
    fun testCopy() {
        val copy = data.copy(version = "2.0.0")
        assertEquals("com.example", copy.groupId)
        assertEquals("my-lib", copy.artifactId)
        assertEquals("2.0.0", copy.version)
    }
}
