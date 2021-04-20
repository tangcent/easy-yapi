package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.itangcent.common.logger.traceError
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.intellij.extend.toInt
import com.itangcent.intellij.file.LocalFileRepository

/**
 * cache:
 * projectToken -> projectId
 */
open class YapiCachedApiHelper : DefaultYapiApiHelper() {

    @Inject
    private val localFileRepository: LocalFileRepository? = null

    private val dbBeanBinderFactory: DbBeanBinderFactory<String> by lazy {
        DbBeanBinderFactory(localFileRepository!!.getOrCreateFile(".api.yapi.v1.1.db").path)
        { "" }
    }

    override fun getProjectIdByToken(token: String): String? {
        try {
            val tokenBeanBinder = dbBeanBinderFactory.getBeanBinder("yapi:token-${yapiSettingsHelper.loginMode().toInt()}:$token")
            val projectIdInCache = tokenBeanBinder.read()
            if (projectIdInCache.isNotBlank()) {
                return projectIdInCache
            }
            val projectIdByApi = super.getProjectIdByToken(token)
            if (!projectIdByApi.isNullOrBlank()) {
                tokenBeanBinder.save(projectIdByApi)
            }
            return projectIdByApi
        } catch (e: Exception) {
            logger.traceError("failed getProjectIdByToken by cache", e)
            return super.getProjectIdByToken(token)
        }
    }
}