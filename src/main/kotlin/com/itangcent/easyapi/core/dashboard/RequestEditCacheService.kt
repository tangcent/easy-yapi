package com.itangcent.easyapi.core.dashboard

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.cache.ProjectCacheRepository
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.GrpcMetadata
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.grpcMetadata
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.export.isGrpc
import com.itangcent.easyapi.format.spi.toJson
import com.itangcent.easyapi.core.util.json.GsonUtils
import com.itangcent.easyapi.core.util.storage.DbBeanBinder
import com.itangcent.easyapi.core.util.storage.SqliteDataResourceHelper

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

    /**
     * Returns all cached edit keys (`className#methodName`), across both the HTTP and
     * gRPC tables.
     */
    fun allKeys(): Set<String> = httpBeanBinder.allIds() + grpcBeanBinder.allIds()

    /**
     * Deletes the given cached-edit keys from both the HTTP and gRPC tables.
     *
     * Callers are expected to know which table each key belongs to; since keys are
     * `className#methodName`, an HTTP and gRPC endpoint can share the same key, so we
     * delete from both to be safe.
     */
    fun deleteAll(keys: Set<String>) {
        keys.forEach { key ->
            if (key.isBlank()) return@forEach
            httpBeanBinder.delete(key)
            grpcBeanBinder.delete(key)
        }
    }

    /**
     * Clears every cached request edit (both HTTP and gRPC). Irreversible; callers
     * should confirm with the user first.
     */
    fun clearAll() {
        httpBeanBinder.deleteAll()
        grpcBeanBinder.deleteAll()
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
        val headerNames = headers.map { it.name }.toSet()
        return HttpRequestEditCache(
            key = key,
            name = endpoint.name,
            path = meta?.path ?: "",
            method = meta?.method?.name ?: endpoint.metadata.protocol,
            host = host,
            headers = headers.map { EditableKeyValue(it.name, it.value ?: it.example ?: "", it.description) } +
                parameters
                    .filter { it.binding == ParameterBinding.Header && it.name !in headerNames }
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
            body = meta?.body?.let { it.toJson() },
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
            body = meta?.body?.let { it.toJson() }
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

        /**
         * Returns the cached-edit keys whose owning class is no longer present in
         * [liveClassNames], i.e. orphaned edits.
         *
         * Keys are `className#methodName`. The GC unit is deliberately the **class**,
         * not the method: a method temporarily disappearing (branch switch, Dumb mode,
         * a transient compile error) must not evict user edits. Only when the *entire
         * class* is absent from the current snapshot do its records count as orphans
         * (see `.spec/dashboard-request-state.md` decision D4).
         *
         * Pure function, testable without a Project or PSI.
         */
        fun orphanKeys(cachedKeys: Set<String>, liveClassNames: Set<String>): Set<String> =
            cachedKeys.filterTo(mutableSetOf()) { key ->
                val className = key.substringBeforeLast('#')
                className.isEmpty() || className !in liveClassNames
            }
    }
}
