package com.itangcent.easyapi.util

import org.junit.Assert.*
import org.junit.Test

class FileTypeTest {

    @Test
    fun testSuffix() {
        assertEquals("java", FileType.JAVA.suffix())
        assertEquals("kt", FileType.KOTLIN.suffix())
        assertEquals("groovy", FileType.GROOVY.suffix())
    }

    @Test
    fun testAcceptable_javaFile() {
        assertTrue(FileType.acceptable("MyClass.java"))
        assertTrue(FileType.acceptable("com/example/MyClass.java"))
    }

    @Test
    fun testAcceptable_kotlinFile() {
        assertTrue(FileType.acceptable("MyClass.kt"))
        assertTrue(FileType.acceptable("com/example/MyClass.kt"))
    }

    @Test
    fun testAcceptable_groovyFile() {
        assertTrue(FileType.acceptable("MyClass.groovy"))
        assertTrue(FileType.acceptable("com/example/MyClass.groovy"))
    }

    @Test
    fun testAcceptable_unsupportedFile() {
        assertFalse(FileType.acceptable("MyClass.py"))
        assertFalse(FileType.acceptable("MyClass.js"))
        assertFalse(FileType.acceptable("MyClass.ts"))
        assertFalse(FileType.acceptable("MyClass.xml"))
        assertFalse(FileType.acceptable("MyClass.txt"))
    }

    @Test
    fun testAcceptable_emptyString() {
        assertFalse(FileType.acceptable(""))
    }

    @Test
    fun testAcceptable_noExtension() {
        assertFalse(FileType.acceptable("MyClass"))
    }

    @Test
    fun testAcceptable_partialMatch() {
        assertFalse(FileType.acceptable("MyClass.java2"))
        assertFalse(FileType.acceptable("MyClass.kts"))
    }

    @Test
    fun testValues() {
        val values = FileType.values()
        assertEquals(3, values.size)
        assertTrue(values.contains(FileType.JAVA))
        assertTrue(values.contains(FileType.KOTLIN))
        assertTrue(values.contains(FileType.GROOVY))
    }
}
