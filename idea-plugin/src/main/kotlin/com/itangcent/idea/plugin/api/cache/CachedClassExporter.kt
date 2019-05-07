package com.itangcent.idea.plugin.api.cache

import com.google.inject.Inject
import com.google.inject.name.Named
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.itangcent.common.exporter.ClassExporter
import com.itangcent.common.exporter.ParseHandle
import com.itangcent.common.model.Request
import com.itangcent.common.model.RequestHandle
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.FileUtils
import org.apache.commons.lang3.exception.ExceptionUtils

class CachedClassExporter : ClassExporter, Worker {

    var statusRecorder: StatusRecorder = StatusRecorder()

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

    @Inject
    private val fileApiCacheRepository: FileApiCacheRepository? = null

    @Inject
    private val actionContext: ActionContext? = null

    private var disabled: Boolean = false

    override fun export(cls: Any, parseHandle: ParseHandle, requestHandle: RequestHandle) {

        if (disabled || cls !is PsiClass) {
            delegateClassExporter!!.export(cls, parseHandle, requestHandle)
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
                var fileApiCache = fileApiCacheRepository!!.getFileApiCache(path)
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

                fileApiCache = FileApiCache()
                fileApiCache.file = path
                val requests = ArrayList<RequestWithKey>()
                fileApiCache.requests = requests

                statusRecorder.newWork()
                actionContext.runInReadUI {
                    try {
                        delegateClassExporter!!.export(cls, parseHandle) { request ->
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
                            fileApiCacheRepository.saveFileApiCache(path, fileApiCache)
                        }
                    } finally {
                        statusRecorder.endWork()
                    }
                }
            } catch (e: Exception) {
                logger!!.error("error to cache api info," + ExceptionUtils.getStackTrace(e))
                disabled = true

                statusRecorder.newWork()
                actionContext.runInReadUI {
                    try {
                        delegateClassExporter!!.export(cls, parseHandle, requestHandle)
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