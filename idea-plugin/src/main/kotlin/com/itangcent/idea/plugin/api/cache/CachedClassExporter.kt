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
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.FileUtils

class CachedClassExporter : ClassExporter {

    @Inject
    private val logger: Logger? = null

    @Inject
    @Named("delegate_classExporter")
    private val classExporter: ClassExporter? = null

    @Inject
    private val fileApiCacheRepository: FileApiCacheRepository? = null

    private var disabled: Boolean = false

    override fun export(cls: Any, parseHandle: ParseHandle, requestHandle: RequestHandle) {

        if (disabled || cls !is PsiClass) {
            classExporter!!.export(cls, parseHandle, requestHandle)
            return
        }

        try {
            val psiFile = cls.containingFile
            val text = psiFile.text
            val md5 = "${text.length}x${text.hashCode()}"//use length+hashcode
            val path = ActionUtils.findCurrentPath(psiFile)!!
                    .replace("/", "_")
                    .replace("\\", "_")
            var fileApiCache = fileApiCacheRepository!!.getFileApiCache(path)
            if (fileApiCache != null
                    && fileApiCache.lastModified!! > FileUtils.getLastModified(psiFile) ?: System.currentTimeMillis()
                    && fileApiCache.md5 == md5) {
                readApiFromCache(cls, fileApiCache, requestHandle)
                return
            }

            fileApiCache = FileApiCache()
            fileApiCache.file = path
            val requests = ArrayList<RequestWithKey>()
            fileApiCache.requests = requests

            classExporter!!.export(cls, parseHandle) { request ->
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
            fileApiCache.md5 = md5
            fileApiCache.lastModified = System.currentTimeMillis()
            fileApiCacheRepository.saveFileApiCache(path, fileApiCache)
        } catch (e: Exception) {
            logger!!.error("error to cache api info," + e.message)
            disabled = true
            classExporter!!.export(cls, parseHandle, requestHandle)
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