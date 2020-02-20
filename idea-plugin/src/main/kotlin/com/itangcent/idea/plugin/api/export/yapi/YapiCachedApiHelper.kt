package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.intellij.file.LocalFileRepository

/**
 * cache:
 * projectToken -> projectId
 */
open class YapiCachedApiHelper : DefaultYapiApiHelper() {

    @Inject
    private val localFileRepository: LocalFileRepository? = null

    private var dbBeanBinderFactory: DbBeanBinderFactory<String>? = null

    private fun getDbBeanBinderFactory(): DbBeanBinderFactory<String> {
        if (dbBeanBinderFactory == null) {
            synchronized(this) {
                dbBeanBinderFactory = DbBeanBinderFactory(localFileRepository!!.getOrCreateFile(".api.yapi.v1.1.db").path)
                { "" }
            }
        }
        return this.dbBeanBinderFactory!!
    }

    override fun getProjectIdByToken(token: String): String? {
        val tokenBeanBinder = getDbBeanBinderFactory().getBeanBinder("yapi:token:$token")
        val projectIdInCache = tokenBeanBinder.read()
        if (!projectIdInCache.isBlank()) {
            return projectIdInCache
        }
        val projectIdByApi = super.getProjectIdByToken(token)
        if (!projectIdByApi.isNullOrBlank()) {
            tokenBeanBinder.save(projectIdByApi)
        }
        return projectIdByApi
    }
}