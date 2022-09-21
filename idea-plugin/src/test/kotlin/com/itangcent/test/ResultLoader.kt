package com.itangcent.test

import com.itangcent.common.logger.Log
import com.itangcent.common.utils.ResourceUtils
import com.itangcent.mock.toUnixString
import com.itangcent.test.TimeZoneKit.fixTimeZone
import kotlin.reflect.KClass

object ResultLoader : Log() {

    fun load(): String {
        val grandCallerClass = com.intellij.util.ReflectionUtil.getGrandCallerClass()!!
        val rawName = grandCallerClass.name
            .replace('#', '.')
            .replace('$', '.')
        val resource = ResourceUtils.readResource("result/$rawName.txt")
        if (resource.isEmpty()) {
            LOG.warn("no resource [result/$rawName.txt] be found")
        }
        return resource.toUnixString().fixTimeZone()
    }

    fun load(name: String): String {
        val grandCallerClass = com.intellij.util.ReflectionUtil.getGrandCallerClass()!!
        return load(grandCallerClass, name)
    }

    fun load(callerClass: KClass<*>, name: String): String {
        return load(callerClass.java, name)
    }

    fun load(callerClass: Class<*>, name: String): String {
        val rawName = callerClass.name
            .replace('#', '.')
            .replace('$', '.')
        val fileName = "$rawName.$name"
        val resource = ResourceUtils.readResource("result/$fileName.txt")
        if (resource.isEmpty()) {
            LOG.warn("no resource [result/$fileName.txt] be found")
        }
        return resource.toUnixString().fixTimeZone()
    }
}