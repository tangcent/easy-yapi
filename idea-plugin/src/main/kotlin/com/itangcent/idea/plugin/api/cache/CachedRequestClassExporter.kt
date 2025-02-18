package com.itangcent.idea.plugin.api.cache

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.itangcent.cache.CacheIndicator
import com.itangcent.common.exception.ProcessCanceledException
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.DefaultClassExporter
import com.itangcent.idea.plugin.api.export.core.DocHandle
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.idea.psi.resourceMethod
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.FileUtils
import java.io.File
import kotlin.reflect.KClass

@Singleton
class CachedRequestClassExporter : ClassExporter {

    override fun support(docType: KClass<*>): Boolean {
        return delegateClassExporter.support(docType)
    }

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var delegateClassExporter: DefaultClassExporter

    @Inject
    private lateinit var fileApiCacheRepository: FileApiCacheRepository

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    private lateinit var cacheIndicator: CacheIndicator

    //no use cache,no read,no write
    private var disabled: Boolean = false

    override fun export(cls: Any, docHandle: DocHandle): Boolean {

        if (disabled || cls !is PsiClass) {
            return delegateClassExporter.export(cls, docHandle)
        }

        val psiFile = actionContext.callInReadUI { cls.containingFile }!!
        val text = actionContext.callInReadUI { psiFile.text } ?: ""
        val path = ActionUtils.findCurrentPath(psiFile)
            .replace(File.separator, "_")
        actionContext.runAsync {
            try {
                val md5 = "${text.length}x${text.hashCode()}"//use length+hashcode
                var fileApiCache: FileApiCache?
                if (cacheIndicator.useCache) {
                    fileApiCache = fileApiCacheRepository.getFileApiCache(path)
                    if (fileApiCache != null
                        && fileApiCache.lastModified!! >= (FileUtils.getLastModified(psiFile)
                            ?: System.currentTimeMillis())
                        && fileApiCache.md5 == md5
                    ) {

                        if (fileApiCache.requests.notNullOrEmpty()) {
                            actionContext.runInReadUI {
                                readApiFromCache(cls, fileApiCache!!, docHandle)
                            }
                        }
                        return@runAsync
                    }
                }

                fileApiCache = FileApiCache()
                fileApiCache.file = path
                val requests = ArrayList<RequestWithKey>()
                fileApiCache.requests = requests

                actionContext.withBoundary {
                    delegateClassExporter.export(cls, requestOnly { request ->
                        docHandle(request)
                        val fullName = PsiClassUtils.fullNameOfMember(cls, request.resourceMethod()!!)
                        requests.add(
                            RequestWithKey(
                                fullName, request
                            )
                        )
                    })
                }
                actionContext.runAsync {
                    fileApiCache.md5 = md5
                    fileApiCache.lastModified = System.currentTimeMillis()
                    fileApiCacheRepository.saveFileApiCache(path, fileApiCache)
                }
            } catch (e: ProcessCanceledException) {
                return@runAsync
            } catch (e: Exception) {
                logger.traceError("error to cache api info", e)

                disabled = true
                delegateClassExporter.export(cls, docHandle)
            }
        }
        return true
    }

    private fun readApiFromCache(
        cls: PsiClass, fileApiCache: FileApiCache, requestHandle: DocHandle,
    ) {
        fileApiCache.requests?.forEach { request ->
            val method = request.key?.let { PsiClassUtils.findMethodFromFullName(it, cls as PsiElement) }
            if (method == null) {
                logger.warn("${request.key} not be found")
                return@forEach
            }
            request.request().let {
                it.resource = PsiMethodResource(method, cls)
                requestHandle(it)
            }
        }
    }
}