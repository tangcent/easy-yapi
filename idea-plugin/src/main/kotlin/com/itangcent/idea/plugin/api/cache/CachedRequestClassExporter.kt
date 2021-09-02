package com.itangcent.idea.plugin.api.cache

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.itangcent.cache.CacheSwitcher
import com.itangcent.common.exception.ProcessCanceledException
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.CompletedHandle
import com.itangcent.idea.plugin.api.export.core.DocHandle
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.idea.psi.resourceMethod
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.FileUtils
import java.io.File
import kotlin.reflect.KClass

@Singleton
class CachedRequestClassExporter : ClassExporter, Worker, CacheSwitcher {

    override fun support(docType: KClass<*>): Boolean {
        return delegateClassExporter?.support(docType) ?: false
    }

    private var statusRecorder: StatusRecorder = StatusRecorder()

    override fun status(): WorkerStatus {
        return when (delegateClassExporter) {
            is Worker -> statusRecorder.status().and(delegateClassExporter.status())
            else -> statusRecorder.status()
        }
    }

    override fun waitCompleted() {
        when (delegateClassExporter) {
            is Worker -> {
                statusRecorder.waitCompleted()
                delegateClassExporter.waitCompleted()
            }
            else -> {
                statusRecorder.waitCompleted()
            }
        }
    }

    override fun cancel() {
    }

    @Inject
    private lateinit var logger: Logger

    @Inject
    @Named("delegate_classExporter")
    private val delegateClassExporter: ClassExporter? = null

    @Inject(optional = true)
    @Named("class.exporter.read.cache")
    private var readCache: Boolean = true

    @Inject
    private val fileApiCacheRepository: FileApiCacheRepository? = null

    @Inject
    private val actionContext: ActionContext? = null

    //no use cache,no read,no write
    private var disabled: Boolean = false

    override fun export(cls: Any, docHandle: DocHandle, completedHandle: CompletedHandle): Boolean {

        if (disabled || cls !is PsiClass) {
            return delegateClassExporter!!.export(cls, docHandle, completedHandle)
        }

        val psiFile = cls.containingFile
        val text = psiFile.text
        val path = ActionUtils.findCurrentPath(psiFile)!!
            .replace(File.separator, "_")
        statusRecorder.newWork()
        actionContext!!.runAsync {
            try {
                val md5 = "${text.length}x${text.hashCode()}"//use length+hashcode
                var fileApiCache: FileApiCache?
                if (readCache) {
                    fileApiCache = fileApiCacheRepository!!.getFileApiCache(path)
                    if (fileApiCache != null
                        && fileApiCache.lastModified!! > FileUtils.getLastModified(psiFile) ?: System.currentTimeMillis()
                        && fileApiCache.md5 == md5
                    ) {

                        if (fileApiCache.requests.notNullOrEmpty()) {
                            statusRecorder.newWork()
                            actionContext.runInReadUI {
                                try {
                                    readApiFromCache(cls, fileApiCache!!, docHandle, completedHandle)
                                } finally {
                                    statusRecorder.endWork()
                                }
                            }
                        }
                        completedHandle(cls)
                        return@runAsync
                    }
                }

                fileApiCache = FileApiCache()
                fileApiCache.file = path
                val requests = ArrayList<RequestWithKey>()
                fileApiCache.requests = requests

                statusRecorder.newWork()
                actionContext.runInReadUI {
                    try {
                        delegateClassExporter!!.export(cls, requestOnly { request ->
                            docHandle(request)
                            requests.add(
                                RequestWithKey(
                                    PsiClassUtils.fullNameOfMember(cls, request.resourceMethod()!!), request
                                )
                            )
                        }, completedHandle)
                        actionContext.runAsync {
                            fileApiCache.md5 = md5
                            fileApiCache.lastModified = System.currentTimeMillis()
                            fileApiCacheRepository!!.saveFileApiCache(path, fileApiCache)
                        }
                    } finally {
                        statusRecorder.endWork()
                    }
                }
            } catch (e: ProcessCanceledException) {
                //ignore cancel
                completedHandle(cls)
            } catch (e: Exception) {
                logger.traceError("error to cache api info", e)

                disabled = true

                statusRecorder.newWork()
                actionContext.runInReadUI {
                    try {
                        delegateClassExporter!!.export(cls, docHandle, completedHandle)
                    } finally {
                        statusRecorder.endWork()
                    }
                }
            } finally {
                statusRecorder.endWork()
            }
        }
        return true
    }

    private fun readApiFromCache(
        cls: PsiClass, fileApiCache: FileApiCache, requestHandle: DocHandle,
        completedHandle: CompletedHandle
    ) {
        fileApiCache.requests?.forEach { request ->
            val method = request.key?.let { PsiClassUtils.findMethodFromFullName(it, cls as PsiElement) }
            if (method == null) {
                logger?.warn("${request.key} not be found")
                return@forEach
            }
            request.request().let {
                it.resource = PsiMethodResource(method, cls)
                requestHandle(it)
            }
        }
        completedHandle(cls)

    }

    override fun notUserCache() {
        this.readCache = false
    }

    override fun userCache() {
        this.readCache = true
    }
}