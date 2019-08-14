package com.itangcent.idea.plugin.api.cache

import com.google.inject.Inject
import com.google.inject.name.Named
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.itangcent.common.exception.ProcessCanceledException
import com.itangcent.common.exporter.RequestHelper
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.api.export.ClassExporter
import com.itangcent.idea.plugin.api.export.RequestHandle
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.FileUtils
import com.itangcent.intellij.util.traceError

class CachedClassExporter : ClassExporter, Worker {

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
    private val logger: Logger? = null

    @Inject
    @Named("delegate_classExporter")
    private val delegateClassExporter: ClassExporter? = null

    @Inject(optional = true)
    @Named("class.exporter.read.cache")
    private val readCache: Boolean = true

    @Inject
    private val fileApiCacheRepository: FileApiCacheRepository? = null

    @Inject
    private val actionContext: ActionContext? = null

    //no use cache,no read,no write
    private var disabled: Boolean = false

    override fun export(cls: Any, requestHelper: RequestHelper, requestHandle: RequestHandle) {

        if (disabled || cls !is PsiClass) {
            delegateClassExporter!!.export(cls, requestHelper, requestHandle)
            return
        }

        val psiFile = cls.containingFile
        val text = psiFile.text
        val path = ActionUtils.findCurrentPath(psiFile)!!
                .replace("/", "_")
                .replace("\\", "_")
        statusRecorder.newWork()
        actionContext!!.runAsync {
            try {
                val md5 = "${text.length}x${text.hashCode()}"//use length+hashcode
                var fileApiCache: FileApiCache?
                if (readCache) {
                    fileApiCache = fileApiCacheRepository!!.getFileApiCache(path)
                    if (fileApiCache != null
                            && fileApiCache.lastModified!! > FileUtils.getLastModified(psiFile) ?: System.currentTimeMillis()
                            && fileApiCache.md5 == md5) {

                        if (!fileApiCache.requests.isNullOrEmpty()) {
                            statusRecorder.newWork()
                            actionContext.runInReadUI {
                                try {
                                    readApiFromCache(cls, fileApiCache!!, requestHandle)
                                } finally {
                                    statusRecorder.endWork()
                                }
                            }
                        }
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
                        delegateClassExporter!!.export(cls, requestHelper) { request ->
                            requestHandle(request)
                            val tinyRequest = Request()
                            tinyRequest.name = request.name
                            tinyRequest.path = request.path
                            tinyRequest.method = request.method
                            tinyRequest.desc = request.desc
                            tinyRequest.headers = request.headers
                            tinyRequest.paths = request.paths
                            tinyRequest.querys = request.querys
                            tinyRequest.formParams = request.formParams
                            tinyRequest.bodyType = request.bodyType
                            tinyRequest.body = request.body
                            tinyRequest.response = request.response

                            requests.add(RequestWithKey(
                                    PsiClassUtils.fullNameOfMethod(request.resource as PsiMethod)
                                    , tinyRequest
                            ))
                        }
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
            } catch (e: Exception) {
                logger!!.error("error to cache api info")
                logger.traceError(e)
                disabled = true

                statusRecorder.newWork()
                actionContext.runInReadUI {
                    try {
                        delegateClassExporter!!.export(cls, requestHelper, requestHandle)
                    } finally {
                        statusRecorder.endWork()
                    }
                }
            } finally {
                statusRecorder.endWork()
            }
        }
    }

    private fun readApiFromCache(cls: Any, fileApiCache: FileApiCache, requestHandle: RequestHandle) {
        fileApiCache.requests?.forEach { request ->
            val method = request.key?.let { PsiClassUtils.findMethodFromFullName(it, cls as PsiElement) }
            request.request!!.resource = method
            requestHandle(request.request!!)
        }

    }
}