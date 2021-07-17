package com.itangcent.test

import com.itangcent.common.logger.ILogger
import com.itangcent.common.logger.ILoggerProvider
import com.itangcent.common.spi.SpiUtils
import com.itangcent.common.utils.ResourceUtils
import com.itangcent.mock.toUnixString

object ResultLoader {

    fun load(): String {
        val grandCallerClass = com.intellij.util.ReflectionUtil.getGrandCallerClass()!!
        val rawName = grandCallerClass.name
            .replace('#', '.')
            .replace('$', '.')
        val resource = ResourceUtils.readResource("result/$rawName.txt")
        if (resource.isEmpty()) {
            LOG?.warn("no resource [result/$rawName.txt] be found")
        }
        return resource.toUnixString()
    }

    fun load(name: String): String {
        val grandCallerClass = com.intellij.util.ReflectionUtil.getGrandCallerClass()!!
        val rawName = grandCallerClass.name
            .replace('#', '.')
            .replace('$', '.')
        val fileName = "$rawName.$name"
        val resource = ResourceUtils.readResource("result/$fileName.txt")
        if (resource.isEmpty()) {
            LOG?.warn("no resource [result/$fileName.txt] be found")
        }
        return resource.toUnixString()
    }
}


private val LOG: ILogger? = SpiUtils.loadService(ILoggerProvider::class)?.getLogger(ResultLoader::class)
