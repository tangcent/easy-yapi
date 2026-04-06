package com.itangcent.easyapi.dashboard

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.cache.ProjectCacheRepository
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.GrpcMetadata
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.model.grpcMetadata
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.model.isGrpc
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter
import com.itangcent.easyapi.util.GsonUtils
import com.itangcent.easyapi.util.storage.DbBeanBinder
import com.itangcent.easyapi.util.storage.SqliteDataResourceHelper

/**
 * Project-level service for caching user edits to API endpoint requests.
 * 
 * This service persists modifications made by users to endpoint parameters,
 * headers, body, and other request settings. The cached edits are restored
 * when the user revisits an endpoint, providing a seamless editing experience.
 * 
 * Storage is backed by SQLite for reliable persistence across IDE sessions.
 */
@Service(Service.Level.PROJECT)
class RequestEditCacheService(private val project: Project) {

    private val sqliteHelper: SqliteDataResourceHelper by lazy {
        val cacheFile = ProjectCacheRepository.getInstance(project).resolve("request_edits.db")
        SqliteDataResourceHelper(cacheFile)
    }

    private val httpBeanBinder: DbBeanBinder<HttpRequestEditCache> by lazy {
        DbBeanBinder(
            sqliteHelper,
            "http_request_edit",
            { GsonUtils.toJson(it) },
            { GsonUtils.fromJson(it) }
        )
    }

    private val grpcBeanBinder: DbBeanBinder<GrpcRequestEditCache> by lazy {
        DbBeanBinder(
            sqliteHelper,
            "grpc_request_edit",
            { GsonUtils.toJson(it) },
            { GsonUtils.fromJson(it) }
        )
    }

    fun save(endpoint: ApiEndpoint, cache: RequestEditCache, key: String) {
        if (key.isBlank()) return
        when (cache) {
            is HttpRequestEditCache -> httpBeanBinder.save(key, cache.copy(key = key))
            is GrpcRequestEditCache -> grpcBeanBinder.save(key, cache.copy(key = key))
        }
    }

    fun load(endpoint: ApiEndpoint, key: String): RequestEditCache? {
        if (key.isBlank()) return null
        return if (endpoint.isGrpc) {
            grpcBeanBinder.load(key)
        } else {
            httpBeanBinder.load(key)
        }
    }

    fun delete(key: String, isGrpc: Boolean) {
        if (key.isBlank()) return
        if (isGrpc) {
            grpcBeanBinder.delete(key)
        } else {
            httpBeanBinder.delete(key)
        }
    }

    fun createDefaultCache(endpoint: ApiEndpoint, key: String, host: String? = null): RequestEditCache {
        return if (endpoint.isGrpc) {
            createDefaultGrpcCache(endpoint, key, host)
        } else {
            createDefaultHttpCache(endpoint, key, host)
        }
    }

    private fun createDefaultHttpCache(endpoint: ApiEndpoint, key: String, host: String?): HttpRequestEditCache {
        val meta = endpoint.httpMetadata
        val parameters = meta?.parameters ?: emptyList()
        val headers = meta?.headers ?: emptyList()
        return HttpRequestEditCache(
            key = key,
            name = endpoint.name,
            path = meta?.path ?: "",
            method = meta?.method?.name ?: endpoint.metadata.protocol,
            host = host,
            headers = headers.map { EditableKeyValue(it.name, it.value ?: it.example ?: "", it.description) } +
                parameters
                    .filter { it.binding == ParameterBinding.Header }
                    .map { EditableKeyValue(it.name, it.defaultValue ?: it.example ?: "", it.description) },
            pathParams = parameters
                .filter { it.binding == ParameterBinding.Path }
                .map { EditableKeyValue(it.name, it.defaultValue ?: it.example ?: "", it.description) },
            queryParams = parameters
                .filter { it.binding == ParameterBinding.Query || it.binding == ParameterBinding.Cookie }
                .map { EditableKeyValue(it.name, it.defaultValue ?: it.example ?: "", it.description) },
            formParams = parameters
                .filter { it.binding == ParameterBinding.Form }
                .map { EditableKeyValue(it.name, it.defaultValue ?: it.example ?: "", it.description) },
            body = meta?.body?.let { ObjectModelJsonConverter.toJson(it) },
            contentType = meta?.contentType
        )
    }

    private fun createDefaultGrpcCache(endpoint: ApiEndpoint, key: String, host: String?): GrpcRequestEditCache {
        val meta = endpoint.grpcMetadata
        return GrpcRequestEditCache(
            key = key,
            name = endpoint.name,
            host = host,
            serviceName = meta?.serviceName,
            methodName = meta?.methodName,
            packageName = meta?.packageName,
            body = meta?.body?.let { ObjectModelJsonConverter.toJson(it) }
        )
    }

    private fun ApiEndpoint.cacheKey(): String {
        val method = sourceMethod ?: return ""
        val cls = sourceClass ?: method.containingClass ?: return ""
        return fullNameOfMember(cls, method)
    }

    private fun fullNameOfMember(psiClass: PsiClass, psiMethod: PsiMethod): String {
        val className = psiClass.qualifiedName ?: psiClass.name ?: ""
        val methodName = psiMethod.name
        return "$className#$methodName"
    }

    companion object {
        fun getInstance(project: Project): RequestEditCacheService =
            project.getService(RequestEditCacheService::class.java)
    }
}
