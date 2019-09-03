package com.itangcent.idea.plugin.script

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.traceError
import groovy.lang.GroovyClassLoader
import org.codehaus.groovy.control.CompilerConfiguration
import java.io.File

class GroovyActionExtLoader : ActionExtLoader {

    private var groovyClassLoader: GroovyClassLoader? = null

    override fun loadActionExt(dataContext: DataContext, action: String, logger: Logger): ActionExt? {

        val basePath = dataContext.getData(CommonDataKeys.PROJECT)!!.basePath
        val localFile = File("$basePath${File.separator}.easyapi${File.separator}ext${File.separator}${action}Ext.groovy")
        if (!localFile.exists()) {
            return null
        }

        logger.trace("try load ext:$localFile")
        val config = CompilerConfiguration()
        config.sourceEncoding = "UTF-8"
        groovyClassLoader = GroovyClassLoader(GroovyActionExtLoader::class.java.classLoader, config)

        val parseClass: Class<*>?
        try {
            parseClass = groovyClassLoader!!.parseClass(localFile)
        } catch (e: Exception) {
            close()
            e.message?.let { logger.error(it) }
            logger.traceError(e)
            return null
        }

        if (ActionExt::class.java.isAssignableFrom(parseClass)) {
            logger.trace("find ext:${parseClass.name}")
            return parseClass!!.newInstance() as ActionExt?
        }

        close()

        logger.trace("none useable ext be found!")

        return null
    }

    fun close() {
        groovyClassLoader?.close()
    }
}