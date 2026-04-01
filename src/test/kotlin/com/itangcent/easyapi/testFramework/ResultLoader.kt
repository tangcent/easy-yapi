package com.itangcent.easyapi.testFramework

import java.io.InputStreamReader
import kotlin.reflect.KClass

object ResultLoader {

    fun load(): String {
        val callerClass = getCallerClass()
        return load(callerClass, "")
    }

    fun load(name: String): String {
        val callerClass = getCallerClass()
        return load(callerClass, name)
    }

    fun load(callerClass: KClass<*>, name: String): String {
        return load(callerClass.java, name)
    }

    fun load(callerClass: Class<*>, name: String): String {
        val rawName = callerClass.name
            .replace('#', '.')
            .replace('$', '.')
        val fileName = if (name.isEmpty()) rawName else "$rawName.$name"
        val resourcePath = "result/$fileName.txt"

        val stream = javaClass.getResourceAsStream("/$resourcePath")
            ?: throw AssertionError("Expected result file not found: $resourcePath")
        
        return InputStreamReader(stream, Charsets.UTF_8).readText()
            .replace("\r\n", "\n")
            .trimEnd()
    }

    fun loadOrNull(name: String): String? {
        return try {
            val callerClass = getCallerClass()
            load(callerClass, name)
        } catch (e: Exception) {
            null
        }
    }

    fun loadOrNull(callerClass: Class<*>, name: String): String? {
        return try {
            load(callerClass, name)
        } catch (e: Exception) {
            null
        }
    }

    private fun getCallerClass(): Class<*> {
        val stackTrace = Thread.currentThread().stackTrace
        for (element in stackTrace) {
            if (element.className != ResultLoader::class.java.name &&
                !element.className.startsWith("java.lang.Thread") &&
                !element.className.contains("ResultLoader")
            ) {
                return try {
                    Class.forName(element.className)
                } catch (e: ClassNotFoundException) {
                    throw IllegalStateException("Could not load caller class: ${element.className}")
                }
            }
        }
        throw IllegalStateException("Could not determine caller class")
    }
}
