package com.itangcent.easyapi.util.ide

import org.junit.Assert.*
import org.junit.Test

class MavenIdDataTest {

    @Test
    fun testMavenFormat() {
        val data = MavenIdData("com.example", "my-app", "1.0.0")
        val maven = data.maven()
        assertTrue("Should contain groupId", maven.contains("<groupId>com.example</groupId>"))
        assertTrue("Should contain artifactId", maven.contains("<artifactId>my-app</artifactId>"))
        assertTrue("Should contain version", maven.contains("<version>1.0.0</version>"))
    }

    @Test
    fun testGradleKotlinFormat() {
        val data = MavenIdData("com.example", "my-app", "1.0.0")
        assertEquals("""implementation("com.example:my-app:1.0.0")""", data.gradleKotlin())
    }

    @Test
    fun testGradleGroovyFormat() {
        val data = MavenIdData("com.example", "my-app", "1.0.0")
        assertEquals("""implementation 'com.example:my-app:1.0.0'""", data.gradleGroovy())
    }

    @Test
    fun testSbtFormat() {
        val data = MavenIdData("com.example", "my-app", "1.0.0")
        assertEquals("""libraryDependencies += "com.example" % "my-app" % "1.0.0"""", data.sbt())
    }
}
